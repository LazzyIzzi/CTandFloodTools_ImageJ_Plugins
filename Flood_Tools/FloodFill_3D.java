package Flood_Tools;

import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.StackStatistics;

import jhd.FloodFill.FloodFill;
import jhd.FloodFill.Offsets.PointDesc;

//***********************************************************************************************

public class FloodFill_3D implements PlugInFilter {

	private class DialogParams
	{
		int neighbors;
		double floodMin;
		double floodMax;
		double fillVal;
		boolean showVoxCnt;
	}

	ImagePlus imp;

	@Override
	public int setup(String arg, ImagePlus imp) {
		this.imp = imp;
		return DOES_8G+DOES_16+DOES_32+STACK_REQUIRED;
	}


	@Override
	public void run(ImageProcessor ip)
	{
		DialogParams dp = doMyDialog();
		if(dp!=null) doMyRoutine(dp);		
	}

	//***********************************************************************************************

	private void doMyRoutine(DialogParams dp)
	{
		int imgWidth = imp.getWidth();
		int imgHeight = imp.getHeight();
		int imgDepth = imp.getNSlices();
		Object[] oImageArr = imp.getStack().getImageArray();
		Roi roi = imp.getRoi();
		FloodFill floodFill = new FloodFill();		
		List<PointDesc> seeds = new ArrayList<>();
		
		int voxelsFlooded=0;

		switch(roi.getType())
		{
		case Roi.POINT:
			Polygon p = roi.getPolygon();
			PointRoi pRoi = (PointRoi)roi;
			for(int i=0;i<p.npoints;i++)
			{
				seeds.add(new PointDesc(p.xpoints[i],p.ypoints[i],pRoi.getPointPosition(i)-1));
			}
			voxelsFlooded = floodFill.grayFloodFill3D(oImageArr, imgWidth, imgHeight, imgDepth,  dp.neighbors,  dp.floodMin,  dp.floodMax, dp.fillVal, seeds);		
			break;
		case Roi.FREELINE:
			int slice = imp.getSlice();
			p = roi.getPolygon();
			for(int i =0;i<p.npoints;i++)
			{
				seeds.add(new PointDesc(p.xpoints[i],p.ypoints[i],slice));
			}
			voxelsFlooded = floodFill.grayFloodFill3D(oImageArr, imgWidth, imgHeight, imgDepth,  dp.neighbors,  dp.floodMin,  dp.floodMax, dp.fillVal, seeds);
			break;
		default:
			IJ.error("FloodFill_Test", "Please use a Point or Freeline roi tool to make a selection");
			break;
		}

		imp.updateAndDraw();
		IJ.run(imp, "Enhance Contrast", "saturated=0.35");
		
		if(dp.showVoxCnt)
		{
			ResultsTable rt = ResultsTable.getResultsTable("FloodFill 3D Results");
			if(rt==null) rt = new ResultsTable();
			rt.incrementCounter();
			rt.addValue("Image", imp.getTitle());
			rt.addValue("Flood Min", dp.floodMin);
			rt.addValue("Flood Max", dp.floodMax);
			rt.addValue("Fill Value", dp.fillVal);
			rt.addValue("Connectivity",dp.neighbors);
			rt.addValue("Voxel Count", voxelsFlooded);
			rt.show("FloodFill 3D Results");
		}
	}

	//***********************************************************************************************

	private DialogParams doMyDialog()
	{
		Roi roi = imp.getRoi();
		if(roi==null )
		{
			IJ.error("FloodFill_Test", "Please use a Point or Freeline roi tool to make a selection");
			return null;
		}
		
		int roiType = roi.getType();
		if(roiType!=Roi.POINT &&  roiType!=Roi.FREELINE)
		{
			IJ.error("FloodFill_Test", "Please use a Point or Freeline roi tool to make a selection");
			return null;
		}


		DialogParams dp = new DialogParams();

		GenericDialog gd = new GenericDialog("Flood Fill 3D");
		String[] conChoices = FloodFill.GetConnectivityChoices();

		//StackStatistics call depends on the current ROI
		imp.resetRoi();
		ImageStatistics stkStats = new StackStatistics(imp);
		imp.setRoi(roi,true);
		
		gd.addMessage("Flood voxel values between Min and Max");
		gd.addChoice("Voxel Connectivity", conChoices, conChoices[2]);
		gd.addNumericField("Flood Min limit", stkStats.min);
		gd.addNumericField("Flood Max limit", stkStats.max);
		gd.addMessage("The fill value must be valid for the image bit depth\n"
				+ "and not lie between Flood Min and Flood Max");
		gd.addNumericField("Flood Fill Voxel value", 255);
		gd.addCheckbox("Show Flooded Count", true);
		gd.addHelp("https://lazzyizzi.github.io/FloodFill.html");

		gd.showDialog();

		if(gd.wasCanceled()) return null;

		dp.neighbors = FloodFill.GetConnectivityValue(gd.getNextChoice());
		dp.floodMin = gd.getNextNumber();
		dp.floodMax = gd.getNextNumber();
		dp.fillVal = gd.getNextNumber();
		dp.showVoxCnt = gd.getNextBoolean();

		return dp;
	}

}
