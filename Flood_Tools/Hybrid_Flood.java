package Flood_Tools;


import java.awt.Color;
import java.awt.Font;

//import java.awt.Color;

import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.filter.*;
import ij.measure.*;

import jhd.FloodFill.*;
import jhd.FloodFill.HybridFloodFill.FloodReport;
import jhd.FloodFill.HybridFloodFill.PorosityReport;


public class Hybrid_Flood implements PlugInFilter
{
	ImagePlus imp;

	//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

	class DialogParams
	{
		public DialogParams() {};
		public float	floodVal;	//fill floodable voxels with this value
		public float	floodMin;	//fill floodable voxels with this value
		public float	floodMax;	//fill floodable voxels with this value
		public int		neighbors;	//6, 18, or 26 Connected
		public String	conChoice;	//"Face","Face & Edge","Face, Edge &Corners" touching neighbor voxels
		public boolean	showResults;//Display statistics of the flood
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
		dp.floodVal=255;
		dp.conChoice = "Face, Edge &Corners";
		dp.neighbors = 26;
		return dp;
	}

	//**********************************************************************************************

	private DialogParams DoMyDialog(DialogParams dp)
	{		

		//dir = IJ.getDirectory("plugins");
		//dir= dir.replace("\\","/");
		//myURL = "file:///"+dir + "FloodFill/FloodFillHelp/FloodFill_FromTopSlice_3D.htm";

		//Get the maximum voxel value and minimum non-zero voxel value
		ImageStack stack = imp.getStack();
		Object[] oImageArr = stack.getImageArray();
		//dp.floodMax = Float.MIN_VALUE;
		dp.floodMin = Float.MAX_VALUE;
		float[] slice;
		int d = stack.getSize();
		for(int i = 0; i< d;i++)
		{
			slice= (float[]) oImageArr[i];
			for(int k = 0; k< slice.length;k++)
			{
				//if(slice[k] > dp.floodMax) dp.floodMax = slice[k];
				if(slice[k] < dp.floodMin && slice[k] > 0) dp.floodMin = slice[k];
			}
		}

		HybridFloodFill hff = new HybridFloodFill();
		String[] conChoices = hff.GetConnectivityChoices();
		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);		
		String msg = "This plugin requires a Hybrid Porosity Image.\n"
				+ "i.e. resolved pores 3D EDM mapped\n"
				+ "unresolved porosity values (0-1)\n"
				+ "Pixel scales > 1, e.g 10um/pixel.";

		GenericDialog gd = new GenericDialog("HybridFlood");
		gd.setInsets(0,0,0);
		gd.addMessage(msg,myFont,Color.BLACK);
		gd.addChoice("Connectivity",conChoices,dp.conChoice);
		gd.addNumericField("Flood Minimum radius ", dp.floodMin, 3);
		//gd.addNumericField("Flood Maximum pixel value", dp.floodMax, 3);
		//gd.addNumericField("Connected voxel fill value:", dp.floodVal, 3);
		gd.addHelp("https://lazzyizzi.github.io/FloodFill.html");
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
		dp.floodMin= (float)gd.getNextNumber();
		//dp.floodMax= (float)gd.getNextNumber();
		//dp.floodVal = (float)gd.getNextNumber();

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
		if(dp.floodVal <= dp.floodMax && dp.floodVal >= dp.floodMin)
		{
			IJ.showMessage("Flood Value (" +dp.floodVal +") must not be between " +dp.floodMin + " and " + dp.floodMax);
			result = false;
		}
		return result;
	}

	//**********************************************************************************************	

	public void run(ImageProcessor ip)
	{
		DialogParams dp = GetDefaultParams();

		HybridFloodFill hff = new HybridFloodFill();
		
		int w = imp.getStack().getWidth();
		int h = imp.getStack().getHeight();
		int d = imp.getStack().getSize();

		dp = DoMyDialog(dp);
		if(dp!=null)
		{
			if(ValidateParams(dp))
			{

				Calibration cal = imp.getCalibration();
				float pw = (float)cal.pixelWidth;
				float ph = (float)cal.pixelHeight;
				float pd = (float)cal.pixelDepth;
				String pu = cal.getUnit();

				Object[] oImageArr = imp.getStack().getImageArray();
				
				FloodReport fldRpt = hff.hybridFloodFill(oImageArr,w,h,d,pw,ph,pd,pu,dp.floodMin, dp.neighbors);

				ShowResults(dp,fldRpt,dp.floodMin);


			}
		}
	}
	
	private void ShowResults(DialogParams dp, FloodReport fldRpt, double testMin)
	{		
		ResultsTable rt;
		
		String unit = imp.getCalibration().getXUnit();
		
		rt = ResultsTable.getResultsTable("Flood Results");		
		if(rt == null) rt = new ResultsTable();
		rt.setPrecision(4);
		//rt.setDecimalPlaces(0, 4); //not effective???
		rt.incrementCounter();
		//Flood statistics
		rt.addValue("Flood Min " +unit, testMin);
		rt.addValue("Floodable VoxCnt", fldRpt.beforeFlood.floodableVoxelCount);
		rt.addValue("Res Vox Cnt", fldRpt.beforeFlood.resolvedVoxelCount);
		rt.addValue("Unres Vox Cnt", fldRpt.beforeFlood.unresolvedVoxelCount);
		rt.addValue("Res Vol " +unit+(char)0x0b3, fldRpt.beforeFlood.resolvedVolume);
		rt.addValue("Unres Vol " +unit+(char)0x0b3, fldRpt.beforeFlood.unresolvedVolume);
		rt.addValue("Res Phi%", fldRpt.beforeFlood.resolvedPorosity*100);
		rt.addValue("Unres Phi%", fldRpt.beforeFlood.unresolvedPorosity*100);
		rt.addValue("Total Phi%", fldRpt.beforeFlood.totalPorosity*100);
		
		rt.addValue("Post Floodable VoxCnt", fldRpt.afterFlood.floodableVoxelCount);
		rt.addValue("Post Res Vox Cnt", fldRpt.afterFlood.resolvedVoxelCount);
		rt.addValue("Post Unres Vox Cnt", fldRpt.afterFlood.unresolvedVoxelCount);
		rt.addValue("Post Res Vol " +unit+(char)0x0b3, fldRpt.afterFlood.resolvedVolume);
		rt.addValue("Post Unres Vol " +unit+(char)0x0b3, fldRpt.afterFlood.unresolvedVolume);
		rt.addValue("Post Res Phi%", fldRpt.afterFlood.resolvedPorosity*100);
		rt.addValue("Post Unres Phi%", fldRpt.afterFlood.unresolvedPorosity*100);
		rt.addValue("Post Total Phi%", fldRpt.afterFlood.totalPorosity*100);
		
		rt.addValue("Flood Cycles", fldRpt.floodStatistics.totalCycles);
		rt.addValue("Contact Cycles", fldRpt.floodStatistics.contactCycles);
		if(fldRpt.floodStatistics.contact) rt.addValue("Contact", "True");
		else  rt.addValue("Contact", "False");
		rt.addValue("Mean Tort", fldRpt.floodStatistics.meanTort);
		rt.addValue("Tort stdDev", fldRpt.floodStatistics.stdDevTort);
		
		rt.show("Flood Results");
	}


}

