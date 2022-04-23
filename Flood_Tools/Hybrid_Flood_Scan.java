package Flood_Tools;


import java.awt.Color;
import java.awt.Font;

//import FloodFill.Flood_Connectivity_Search_V3.DialogParams;

//import java.awt.Color;

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

public class Hybrid_Flood_Scan implements PlugInFilter
{
	ImagePlus imp;
	final static boolean doEDM = true;
	final static boolean noEDM = false;
	final static boolean doGDT = true;
	final static boolean noGDT = false;
//	HybridFloodFill_V3 hff = new HybridFloodFill_V3();
	HybridFloodFill hff = new HybridFloodFill();
	FloodReport fldRpt = new FloodReport();

	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	class DialogParams
	{
		public DialogParams() {};
		public double	floodMin;	//the smallest radius or porosity to flood
		public double	floodMax;	//the largest radius or porosity to flood
		public double	floodInc;	//Step size for the scan between min and max
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

		//this converts the phi map to a hybrid map with EDM in the resolved pores
		//The EDM will not be needed again so we call hybridFloodFill with noEDM
		ImagePlus tmpImp = imp.duplicate();
		Object[] oTempArr= tmpImp.getStack().getImageArray();
		Calibration cal = tmpImp.getCalibration();
		int w=imp.getWidth();
		int h=imp.getHeight();
		int d=imp.getStack().getSize();

		hff.phiMapToHybridMap(oTempArr,w,h,d,cal.pixelWidth,cal.pixelHeight,cal.pixelDepth);
		
		//Get the maximum voxel value and minimum non-zero voxel value
//		ImageStack stack = imp.getStack();
//		Object[] oImageArr = stack.getImageArray();
		dp.floodMax = Float.MIN_VALUE;
		dp.floodMin = Float.MAX_VALUE;
		float[] slice;
		for(int i = 0; i< d;i++)
		{
			slice= (float[]) oTempArr[i];
			for(int k = 0; k< slice.length;k++)
			{
				if(slice[k] > dp.floodMax) dp.floodMax = slice[k];
				if(slice[k] < dp.floodMin && slice[k] > 0) dp.floodMin = slice[k];
			}
		}
		tmpImp.changes=false;
		tmpImp.close();
		
		String[] conChoices = HybridFloodFill.GetConnectivityChoices();
		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);		
		String msg = "This plugin requires a Porosity Image.\n"
				+ "resolved open pores = 1 \n"
				+ "unresolved porosity values 0<phi<1\n"
				+ "Pixel height, width, depth must be > 1unit/pixel,\n"
				+ "e.g 5um/pixel width, 7um/pixel height, 10um/pixel depth.";

		GenericDialog gd = new GenericDialog("Hybrid Flood Scan");
		gd.setInsets(0,0,0);
		gd.addMessage(msg,myFont,Color.BLACK);
		gd.addChoice("Connectivity",conChoices,dp.conChoice);
		gd.addNumericField("Flood Minimum pixel value", dp.floodMin, 3);
		gd.addNumericField("Flood Maximum pixel value", dp.floodMax, 3);
		gd.addNumericField("Flood Increment", dp.floodInc, 3);
		//gd.addNumericField("Connected voxel fill value:", dp.floodVal, 3);
		gd.addCheckbox("Save Images", dp.saveImages);
		gd.addCheckbox("Show Plot", dp.showPlot);
		gd.addHelp("https://lazzyizzi.github.io/HybridFloodScan.html");
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
		dp.floodMin= gd.getNextNumber();
		dp.floodMax= gd.getNextNumber();
		dp.floodInc= gd.getNextNumber();
		dp.saveImages = gd.getNextBoolean();
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

		//How to declare a non-static public nested class
		String dir=null, basename=null,fileName=null;
		ImagePlus dupImp;
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
				hff.phiMapToHybridMap(oHybridArr, w, h, d, pw, ph, pd);
				//hybridImp.show();
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
	
				// the main loop for scanning the volume connectivity
				for(double floodR = dp.floodMin; floodR <= dp.floodMax; floodR+=dp.floodInc)
				{
					dupImp = hybridImp.duplicate();
					Object[] oImageArr = dupImp.getStack().getImageArray();
					fldRpt = hff.hybridFloodFill(oImageArr,w,h,d,pw,ph,pd,pu,floodR, dp.neighbors,noEDM,doGDT);
					if(dp.saveImages)
					{
						fileName = basename + IJ.d2s((double)floodR,4);
						IJ.saveAs(dupImp, "Tiff", dir+fileName+".tif");
						IJ.showStatus("Scanning R="+floodR);
					}
					dupImp.close();
					showFloodResults(dp,fldRpt,floodR,fileName);
				}	

				//Special plotter colors contact image values red
				if(dp.showPlot)
				{
					ResultsTable rt;						
					rt = ResultsTable.getResultsTable("Flood Results");
					if(rt!=null)
					{
						String[] contact = rt.getColumnAsStrings("Breakthrough");
						//get "True" counts
						int trueCnt=0;
						for(int i=0;i<contact.length;i++)
						{
							if(contact[i].equals("True")) trueCnt++;
						}
						
						//All of this min max stuff because plot.setLimitsToFit(false) or true; does not work!!
						double[] trueMinR = new double[trueCnt];
						double[] trueFldCnt = new double[trueCnt];
						double[] falseMinR = new double[contact.length-trueCnt];
						double[] falseFldCnt = new double[contact.length-trueCnt];
						
						double maxR=Float.MIN_VALUE;
						double minR=Float.MAX_VALUE;
						
						double maxCnt=Float.MIN_VALUE;
						double minCnt=Float.MAX_VALUE;
						String unit = imp.getCalibration().getXUnit();
					
						for(int i = 0, t=0, f=0; i<contact.length;i++)
						{
							if(contact[i].equals("True"))
							{
								trueMinR[t] = rt.getValue("Min R "+unit, i);
								if(trueMinR[t] < minR) minR = trueMinR[t];
								if(trueMinR[t] > maxR) maxR = trueMinR[t];
								trueFldCnt[t] = rt.getValue("Tot Flood "+unit+(char)0x0b3, i);	
								if(trueFldCnt[t] < minCnt) minCnt = trueFldCnt[t];
								if(trueFldCnt[t] > maxCnt) maxCnt = trueFldCnt[t];
								t++;
							}
							else
							{
								falseMinR[f] = rt.getValue("Min R "+unit, i);
								if(falseMinR[f] < minR) minR = falseMinR[f];
								if(falseMinR[f] > maxR) maxR = falseMinR[f];
								falseFldCnt[f] = rt.getValue("Tot Flood "+unit+(char)0x0b3, i);	
								if(falseFldCnt[f] < minCnt) minCnt = falseFldCnt[f];
								if(falseFldCnt[f] > maxCnt) maxCnt = falseFldCnt[f];
								f++;								
							}
						}
						
						Plot plot = new Plot("Flood Scan Results","Min R "+unit,"Tot Flood "+unit+(char)0x0b3);
						plot.addPoints(falseMinR, falseFldCnt, Plot.CIRCLE);
						//plot.add("circle", falseMinR, falseFldCnt);
						plot.setStyle(0,"black,black,1,circle");
						plot.addPoints(trueMinR, trueFldCnt, Plot.CIRCLE);
						//plot.add("circle", trueMinR, trueFldCnt);
						plot.setStyle(1,"red,red,1,circle");
						//plot.setLimitsToFit(true); //this does not work!!
						plot.setLimits(minR,maxR,minCnt, maxCnt);
						plot.show();
					}
				}
			}
		}
	}


	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	//private void showFloodResults(DialogParams dp, HybridFloodFill_V3.FloodReport fldRpt, double testMin, String fileName)
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

