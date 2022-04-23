package Flood_Tools;
//Given an image or a stack with one or more a point rois, return the ixCol,jyRow,kzSlice and the value.

import java.awt.Polygon;
import java.util.ArrayList;
import java.util.List;

//import ij.IJ;
import ij.gui.*;
import ij.measure.ResultsTable;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.*;
import ij.process.ImageProcessor;

import jhd.FloodFill.Offsets.PointDesc;

public class Get_Points_And_Values implements PlugInFilter {

	//*********************************************************************************************


	public Get_Points_And_Values() {
		// TODO Auto-generated constructor stub
	}

	ImagePlus imp;

	@Override
	public int setup(String arg, ImagePlus imp) {
		// TODO Auto-generated method stub
		this.imp=imp;
		return DOES_ALL;
	}

	@Override
	public void run(ImageProcessor ip)
	{
		List<PointDesc> myPts;
		//Test to see if the roi is 2D or 3D
		Roi roi= imp.getRoi();
		if(roi.getType()==Roi.POINT)
		{
			PointRoi pointRoi = (PointRoi) roi;
			//pointRoi.getPosition() Returns the stack position (image number) of this ROI
			//or zero if the ROI is not associated with a particular stack image.
			int slice2D = pointRoi.getPosition();
			if(slice2D ==0)
			{
				myPts = GetSlicePoints3D();
			}
			else
			{
				myPts = GetSlicePoints2D(slice2D);				
			}			
			ResultsTable rt = ResultsTable.getResultsTable("Point Value Results");
			if(rt==null) rt = new ResultsTable();
			for(PointDesc pd : myPts)
			{
				rt.incrementCounter();
				rt.addValue("X", pd.x);
				rt.addValue("Y", pd.y);
				rt.addValue("Z", pd.z);
				rt.addValue("Val", pd.val);
			}
			rt.show("Point Value Results");
		}		
	}
		
	//********************************************************************************
	
	private List<PointDesc> GetSlicePoints3D()
	{
		int ixCol,jyRow,kzSlice;
		List<PointDesc> slicePoints =new ArrayList<>();
		
		Roi roi= imp.getRoi();
		ImageStack stk = imp.getStack();
		
		if(roi.getType()==Roi.POINT)
		{
			PointRoi pRoi = (PointRoi) roi;
			Polygon p = roi.getPolygon();
			
			for(int i = 0; i<p.npoints;i++)
			{
				ixCol = p.xpoints[i];
				jyRow = p.ypoints[i];
				kzSlice = pRoi.getPointPosition(i)-1;
				double val =stk.getVoxel(ixCol, jyRow, kzSlice);
				slicePoints.add(new PointDesc(ixCol, jyRow, kzSlice,val));
			}
		}	
		return slicePoints;
	}
	
	//********************************************************************************

	private List<PointDesc> GetSlicePoints2D(int slice2D)
	{
		int ixCol,jyRow;
		List<PointDesc> slicePoints =new ArrayList<>();
		
		Roi roi= imp.getRoi();
		ImageStack stk = imp.getStack();
		
		if(roi.getType()==Roi.POINT)
		{
			Polygon p = roi.getPolygon();			
			for(int i = 0; i<p.npoints;i++)
			{
				ixCol = p.xpoints[i];
				jyRow = p.ypoints[i];
				double val =stk.getVoxel(ixCol, jyRow, slice2D-1);
				slicePoints.add(new PointDesc(ixCol, jyRow, slice2D-1,val));
			}
		}	
		return slicePoints;
	}


}
