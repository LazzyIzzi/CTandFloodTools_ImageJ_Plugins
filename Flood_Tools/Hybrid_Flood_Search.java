package Flood_Tools;


import java.awt.Color;
import java.awt.Font;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.filter.*;
import ij.measure.*;

//import jhd.DistanceMaps.libJ8.*;
//import jhd.DistanceMaps.libJ8.HybridFloodFill_V3.FloodReport;
//import jhd.DistanceMaps.libJ8.HybridFloodFill_V3.PorosityReport;

import jhd.FloodFill.HybridFloodFill;
import jhd.FloodFill.HybridFloodFill.FloodReport;
import jhd.FloodFill.HybridFloodFill.PorosityReport;

public class Hybrid_Flood_Search implements PlugInFilter
{
	ImagePlus imp;
	final static boolean doEDM = true;
	final static boolean noEDM = false;
	final static boolean doGDT = true;
	final static boolean noGDT = false;
	
	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	class DialogParams
	{
		public DialogParams() {};
		public double	floodInc;	//The minimum voxel value to flood
		//public double	floodVal;	//fill floodable voxels with this value
		public double	floodMin;	//fill floodable voxels with this value
		public double	floodMax;	//fill floodable voxels with this value
		public int		neighbors;	//6, 18, or 26 Connected
		public String	conChoice;	//"Face","Face & Edge","Face, Edge &Corners" touching neighbor voxels
		public boolean	showResults;//Display statistics of the flood
		public boolean	saveImages;
		public boolean	showBreakImage;
		public boolean	showPlot;
	}

	//**********************************************************************************************

	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_32+STACK_REQUIRED+NO_UNDO;
	}

	//**********************************************************************************************

	private DialogParams GetDefaultParams()
	{
		DialogParams dp = new DialogParams();
		dp.floodInc = 0.01f;
		dp.conChoice = "Face, Edge &Corners";
		dp.neighbors = 26;
		dp.saveImages=false;
		dp.showBreakImage=false;
		dp.showPlot=false;
		return dp;
	}

	//**********************************************************************************************

	private DialogParams DoMyDialog(DialogParams dp)
	{		

		//dir = IJ.getDirectory("plugins");
		//dir= dir.replace("\\","/");
		//myURL = "file:///"+dir + "FloodFill/FloodFillHelp/FloodFill_FromTopSlice_3D.htm";

//		HybridFloodFill_V3 hff = new HybridFloodFill_V3();
//		HybridFloodFill hff = new HybridFloodFill();
		String[] conChoices = HybridFloodFill.GetConnectivityChoices();
		
		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
		
		String msg = "This plugin requires a Porosity Image.\n"
				+ "resolved open pores = 1 \n"
				+ "unresolved porosity values 0<phi<1\n"
				+ "Pixel height, width, depth must be > 1unit/pixel,\n"
				+ "e.g 5um/pixel width, 7um/pixel height, 10um/pixel depth.";
		
		String unit = imp.getCalibration().getUnit();
		
		GenericDialog gd = new GenericDialog("Hybrid Flood Search");
		gd.setInsets(0,0,0);
		gd.addMessage(msg,myFont,Color.BLACK);
		gd.addChoice("Connectivity",conChoices,dp.conChoice);
		gd.addNumericField("Stop at Search delta("+unit+")", dp.floodInc, 2);
		gd.addCheckbox("Save Images", dp.saveImages);
		gd.addCheckbox("Show breakthrough Image", dp.showBreakImage);
		gd.addCheckbox("Show Plot", dp.showPlot);
		gd.addHelp("https://lazzyizzi.github.io/HybridFloodSearch.html");
		gd.showDialog();

		if(gd.wasCanceled()) return null;

		int choiceIndex = gd.getNextChoiceIndex();
		dp.conChoice = conChoices[choiceIndex];
		switch (choiceIndex)
		{
		case 0: dp.neighbors = 6; break;
		case 1: dp.neighbors = 18; break;
		case 2: dp.neighbors = 26; break;
		}
		dp.floodInc= (float)gd.getNextNumber();
		dp.saveImages = gd.getNextBoolean();
		dp.showBreakImage = gd.getNextBoolean();
		dp.showPlot = gd.getNextBoolean();

		return dp;
	}

	//**********************************************************************************************

	private boolean ValidateParams(DialogParams dp)
	{
		boolean result = true;
		if(dp.neighbors != 6 && dp.neighbors != 18 && dp.neighbors != 26)
		{
			IJ.showMessage("Connectivity  must be \"Face\"(6),\"Face & Edge\"(18),\"Face, Edge &Corners\"(26)");
			result = false;
		}
		return result;
	}

	//**********************************************************************************************	

	public void run(ImageProcessor ip)
	{
		DialogParams dp = GetDefaultParams();
		
//		HybridFloodFill_V3 hff = new HybridFloodFill_V3();
		HybridFloodFill hff = new HybridFloodFill();
		FloodReport fldRpt = new FloodReport();
		String dir=null, basename=null,fileName=null;
		ImagePlus dupImp;
		Object[] oImageArr;
		int w = imp.getStack().getWidth();
		int h = imp.getStack().getHeight();
		int d = imp.getStack().getSize();
		
		
		dp = DoMyDialog(dp);
		if(dp!=null)
		{
			if(ValidateParams(dp))
			{
				basename = imp.getTitle();
				int loc = basename.lastIndexOf(".");
				basename = basename.substring(0, loc) + "_";
				if(dp.saveImages) dir = IJ.getDirectory("Choose a Directory");
 				
				Calibration cal = imp.getCalibration();
				double pw = cal.pixelWidth;
				double ph = cal.pixelHeight;
				double pd = cal.pixelDepth;
				String pu = cal.getUnit();
				
				
				//convert porosity map to a hybrid map
				ImagePlus hybridImp = imp.duplicate();									
				Object[] oHybridArr = hybridImp.getStack().getImageArray();
				
				PorosityReport phiRpt = hff.characterize(oHybridArr, w, h, d, pw, ph, pd);
				ResultsTable dryResults;
				
				dryResults = ResultsTable.getResultsTable("Dry Results");		
				if(dryResults == null) dryResults = new ResultsTable();
				dryResults.setPrecision(4);
				dryResults.incrementCounter();
				dryResults.addValue("Resolved Pore Volume "+pu+(char)0x0b3,phiRpt.resolvedVolume);
				dryResults.addValue("Unresolved Pore Volume "+pu+(char)0x0b3,phiRpt.unresolvedVolume);
				dryResults.addValue("Tot Pore Volume "+pu+(char)0x0b3,phiRpt.resolvedVolume + phiRpt.unresolvedVolume);
				
				dryResults.addValue("Resolved "+(char)0x3c6+"%",phiRpt.resolvedPorosity*100);	
				dryResults.addValue("Unresolved "+(char)0x3c6+"%",phiRpt.unresolvedPorosity*100);	
				dryResults.addValue("Tot "+(char)0x3c6+"%",phiRpt.totalPorosity*100);
				dryResults.show("Dry Results");
				
				//this converts the phi map to a hybrid map with EDM in the resolved pores
				//The EDM will not be needed again so we call hybridFloodFill with noEDM
				hff.phiMapToHybridMap(oHybridArr,w,h,d,pw,ph,pd);
							
				dp.floodMax = Float.MIN_VALUE;
				dp.floodMin = Float.MAX_VALUE;
				float[] slice;
				for(int i = 0; i< d;i++)
				{
					slice= (float[]) oHybridArr[i];
					for(int k = 0; k< slice.length;k++)
					{
						if(slice[k] > dp.floodMax) dp.floodMax = slice[k];
						if(slice[k] < dp.floodMin && slice[k] > 0) dp.floodMin = slice[k];
					}
				}
				
				
				IJ.showStatus("Testing Connectivity Limits");
				dupImp = hybridImp.duplicate();
				oImageArr = dupImp.getStack().getImageArray();
				
				fldRpt = hff.hybridFloodFill(oImageArr,w,h,d,pw,ph,pd,pu,dp.floodMin, dp.neighbors,noEDM,doGDT);
				
				dupImp.close();
				boolean floodMinTest = fldRpt.floodStatistics.contact;
				showFloodResults(dp,fldRpt,dp.floodMin,"Not Saved");
				
				dupImp = hybridImp.duplicate();
				oImageArr = dupImp.getStack().getImageArray();
				double testLowerLimit = dp.floodMax -.1f;
				
				fldRpt = hff.hybridFloodFill(oImageArr,w,h,d,pw,ph,pd,pu,testLowerLimit, dp.neighbors,noEDM,doGDT);
				
				dupImp.close();
				boolean floodMaxTest = fldRpt.floodStatistics.contact;
				showFloodResults(dp,fldRpt,testLowerLimit,"Not Saved");
				
				if(floodMinTest == false || floodMaxTest==true)
				{
					IJ.error("Volume connectivity not within test range");
					return;
				}
				//end verify that the volume is connected at floodMin and disconnected at floodMax
				
								
				else //Bisection search for floodMin at breakthrough 
				{
					IJ.showStatus("Bisection Search");
					//A flood always has the floodMax, the largest pore size, as one limit.
					//We want to find the largest value of testLowerLimit that allows the flood to reach the back slice
					
					//Our first guess is at the midpoint
					double lowerLimit	= dp.floodMin;
					double upperLimit	= dp.floodMax;
					testLowerLimit	= (upperLimit+lowerLimit)/2.0;
					
					//We have already determined that the flood connects at floodMin
					//so we set our initial solution to that value.
					double brkMin = lowerLimit;
					boolean done = false;
					
					while(done == false)
					{
						dupImp = hybridImp.duplicate();
						oImageArr = dupImp.getStack().getImageArray();
						
						fldRpt = hff.hybridFloodFill(oImageArr,w,h,d,pw,ph,pd,pu,testLowerLimit, dp.neighbors,noEDM,doGDT);
						
						if(dp.saveImages)
						{
							fileName= basename + IJ.d2s((double)testLowerLimit,4);
							IJ.saveAs(dupImp, "Tiff", dir+fileName+".tif");
							IJ.showStatus("Bisection Search");
						}
						dupImp.close();

						showFloodResults(dp,fldRpt,testLowerLimit,fileName);


						if(fldRpt.floodStatistics.contact)
						{
							lowerLimit = testLowerLimit;
							if(testLowerLimit>brkMin) brkMin =testLowerLimit;
						}
						else
						{
							upperLimit = testLowerLimit;
						}
						
						testLowerLimit	= (upperLimit+lowerLimit)/2.0; 						 
						if(upperLimit-lowerLimit < dp.floodInc) done = true;						
					}
					
					//Recalculate the breakthrough image
					dupImp = hybridImp.duplicate();
					oImageArr = dupImp.getStack().getImageArray();
					fldRpt = hff.hybridFloodFill(oImageArr,w,h,d,pw,ph,pd,pu,brkMin, dp.neighbors,noEDM,doGDT);
					showFloodResults(dp,fldRpt,brkMin,fileName);
					
					//save the breakthrough image
					if(dp.saveImages)
					{
						fileName= basename + IJ.d2s((double)testLowerLimit,4);
						IJ.saveAs(dupImp, "Tiff", dir+fileName+"Brk.tif");
					}
					//display the breakthrough image
					if(dp.showBreakImage)
					{
						fileName= basename + IJ.d2s((double)testLowerLimit,4);
						dupImp.setTitle(fileName+"Brk.tif");
						dupImp.show();
						//Re-scale the display between the min and max distance of the entire stack
						float fmin=Float.MAX_VALUE,fmax=Float.MIN_VALUE;
						float val;				
						for(int k=0;k<d;k++)
						{
							float[] fData= (float[])oImageArr[k];
							for(int j=0;j<fData.length;j++)
							{
								val = fData[j];
								if(val< fmin) fmin=val;
								else if(val>fmax) fmax=val;								
							}
						}				
						dupImp.setDisplayRange(fmin, fmax);
						IJ.run("Fire");
					}
					
					if(dp.showPlot)
					{
						ResultsTable rt;						
						rt = ResultsTable.getResultsTable("Flood Results");
						String unit = imp.getCalibration().getXUnit();

						if(rt!=null)
						{
							double[] minR = rt.getColumn("Min R "+unit);
							double[] fldCnt = rt.getColumn("Tot Flood "+unit+(char)0x0b3);
							Plot plot = new Plot("Flood Results","Min R "+unit,"Tot Flood "+unit+(char)0x0b3);
							plot.add("circle", minR, fldCnt);
							double[] brkR = new double[1];
							double[] brkCnt = new double[1];
							brkR[0] = rt.getValue("Min R "+unit, rt.getCounter()-1);
							brkCnt[0] = rt.getValue("Tot Flood "+unit+(char)0x0b3, rt.getCounter()-1);
							plot.add("circle", brkR, brkCnt);
							plot.setStyle(1,"red,red,1,circle");
							plot.show();
						}						
					}
 				}
			}
		}
	}

	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

//	private void showFloodResults(DialogParams dp, HybridFloodFill_V3.FloodReport fldRpt, double testMin, String fileName)
	private void showFloodResults(DialogParams dp, FloodReport fldRpt, double testMin, String fileName)
	{
		ResultsTable floodResults;					
		
		String unit = imp.getCalibration().getXUnit();
		
		floodResults = ResultsTable.getResultsTable("Flood Results");		
		if(floodResults == null) floodResults = new ResultsTable();
		floodResults.setPrecision(5);
		floodResults.incrementCounter();
		if(dp.saveImages)floodResults.addValue("File Name ",fileName);		
		
		floodResults.addValue("Min R "+unit,testMin);
		//rt.addValue("Max R "+unit,dp.floodMax);
		//rt.addValue("Max Radius "+unit,dp.floodMax*pixelSize);
		//rt.addValue("Flood Val",dp.floodVal);
		floodResults.addValue("Connectivity", dp.neighbors);
		if(fldRpt.floodStatistics.contact == true) floodResults.addValue("Breakthrough","True");
		else floodResults.addValue("Breakthrough","False");
		
//		rt.addValue("Res Flood Voxels",fldRpt.afterFlood.resolvedVoxelCount);
//		rt.addValue("Unres Flood Voxels",fldRpt.afterFlood.unresolvedVoxelCount);
//		rt.addValue("Tot Flood Voxels",fldRpt.afterFlood.resolvedVoxelCount+fldRpt.afterFlood.unresolvedVoxelCount);


		floodResults.addValue("Res Flood "+unit+(char)0x0b3,fldRpt.afterFlood.resolvedVolume);
		floodResults.addValue("Unres Flood "+unit+(char)0x0b3,fldRpt.afterFlood.unresolvedVolume);
		floodResults.addValue("Tot Flood "+unit+(char)0x0b3,fldRpt.afterFlood.resolvedVolume+fldRpt.afterFlood.unresolvedVolume);
		
		ResultsTable dryResults = ResultsTable.getResultsTable("Dry Results");

		double dryResVol = dryResults.getValue("Resolved Pore Volume "+unit+(char)0x0b3, 0);
		double dryUnresVol = dryResults.getValue("Unresolved Pore Volume "+unit+(char)0x0b3, 0);
		double dryTotVol = dryResults.getValue("Tot Pore Volume "+unit+(char)0x0b3, 0);
		
		floodResults.addValue("Res Sat% ",fldRpt.afterFlood.resolvedVolume/dryResVol);
		floodResults.addValue("Unres Sat% ",fldRpt.afterFlood.unresolvedVolume/dryUnresVol);
		floodResults.addValue("Tot Sat% ",(fldRpt.afterFlood.resolvedVolume+fldRpt.afterFlood.unresolvedVolume)/dryTotVol);

		floodResults.addValue("Cycles at Contact",fldRpt.floodStatistics.contactCycles);
		floodResults.addValue("Tot flood Cycles",fldRpt.floodStatistics.totalCycles);
		floodResults.addValue("Tort Mean", fldRpt.floodStatistics.meanTort);
		floodResults.addValue("Tort StdDev", fldRpt.floodStatistics.stdDevTort);
		
		//rt.addValue("Res Flood "+voxelUnit,wholeFloodVol);
		//rt.addValue("Tot Flood "+voxelUnit,totFloodVol );
		floodResults.show("Flood Results");
	}


}

