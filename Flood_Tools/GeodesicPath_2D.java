package Flood_Tools;

/*
Plugin to draw in overlay the shortest path between the origin and a point in a 2D geodesic distance image.
Input: a 32-bit 2D Geodesic transformed image where the non-transformed phase (the obstacles) are set to zero.
	and at least one point in the GDT phase. DO NOT use on tortuosity mapped images.
Output: The approximated shortest path between the point(s) and the nearest GDT distance = 1 is drawn in the image overlay.
Note:	The paths drawn are not necessarily the same as those a human would draw.  This is a limitation of the
	GDT by propagation method used to construct the underlying GDT distance map. Distances can propagate
	in only eight directions. 0,45, 90, 135, 180, 225, 270 and 315 degrees. so the propagating paths make make
	only 45 and 90 degree turns to either side. 
	Increase the iteration count (see code) to draw long paths.
JHD 12/22/19
 */


import ij.*;
import ij.process.*;
import ij.gui.*;
import java.awt.*;
import ij.plugin.filter.*;
import ij.plugin.frame.RoiManager;

import java.util.ArrayList;
import java.util.Properties;

//import jhd.DistanceMaps.libJ8.Offsets.PointDesc;
//import jhd.DistanceMaps.libJ8.*;
import jhd.FloodFill.GDT3D;
import jhd.FloodFill.Offsets.PointDesc;

/**
 * Plugin to draw in overlay the shortest path between the origin and a point in a 2D geodesic distance image.
 * @author John H Dunsmuir
 */
public class GeodesicPath_2D implements PlugInFilter
{

	ImagePlus		imp;
	ImageProcessor	ip;
	PointDesc[]		offsetPts;

	//***********************************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_32+NO_UNDO;
	}

	//***********************************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		RoiManager roiMgr = RoiManager.getRoiManager();
		GDT3D myGdt = new GDT3D();
		ArrayList<PointDesc> probePts = new ArrayList<PointDesc>();
		Object data;
		Polygon p;
		PointRoi pRoi;


		Properties props = imp.getImageProperties();
		if(props==null)
		{
			IJ.showMessage("Missing Image Properties","Please select a 2D Geodesic Transform Image");
			return;
		}
		else
		{
			String source = props.getProperty("GeodesicSource");
			String type = props.getProperty("GeodesicType");
			if(source!=null && type!=null)
			{
				if(source.indexOf("2D")==-1 || type.indexOf("Distance")==-1)
				{
					IJ.showMessage("Incorrect Image Properties","Please select a 2D Geodesic Transform Image");
					return;
				}
			}
			else
			{
				IJ.showMessage("Missing Image Properties","Please select a 2D Geodesic Transform Image");	
				return;
			}
		}

		Roi roi = imp.getRoi();

		if(roi==null  || roi.getType() != Roi.POINT)
		{
			IJ.showMessage("Select one or more points in a GDT area using the multi-Point ROI tool");
			return;
		}

		int width = imp.getWidth();
		int height = imp.getHeight();
		int depth = imp.getNSlices();

		p = roi.getPolygon();
		pRoi = (PointRoi)roi;
		int counterIndex =pRoi.getCounter();
		int nPts = pRoi.getCount(counterIndex);

		ImageStack stk = imp.getStack();

		//Get the probe points
		for(int i=0;i<nPts;i++)
		{
			int x = p.xpoints[i];
			int y = p.ypoints[i];
			int z;
			//getPointPosition Returns the stack slice of the point with the given index, or 0 if no slice defined for this point 
			if(depth>1)
			{
				z = pRoi.getPointPosition(i)-1;
			}
			else
			{
				z=0;
			}
			float val = (float)stk.getVoxel(x, y, z);				
			if(val>0)
			{
				probePts.add(new PointDesc(x,y,z,val));	
			}
		}


		if(probePts.size()>0)
		{
			@SuppressWarnings("unchecked")
			ArrayList<PointDesc>[] geoPaths2D = new ArrayList[probePts.size()];
			int[] zData = new int[probePts.size()];

			//Get the 2D paths for each probePoint
			int pathCnt=0;
			for(PointDesc probePt : probePts)
			{
				data=stk.getPixels(probePt.z+1);									
				geoPaths2D[pathCnt] = myGdt.getGeoPath2D(data,width,height, probePt);
				zData[pathCnt]=probePt.z;
				pathCnt++;
			}

			if(geoPaths2D!=null)
			{
				if(roiMgr==null)
				{
					roiMgr = new RoiManager();
				}
				int i=roiMgr.getCount();

				//PolygonRoi pathRoi=null;
				Prefs.useNamesAsLabels=true;
				Prefs.showAllSliceOnly=true;

				//geoPaths2D has no slice information
				int j=0;
				for(ArrayList<PointDesc> pl : geoPaths2D)
				{
					PointRoi ptRoi = new PointRoi();
					ptRoi.setOptions("small dot red");
					ptRoi.setPosition(zData[j]+1);
					for(PointDesc point : pl)
					{
						ptRoi.addPoint(point.x,point.y);
					}
					roiMgr.addRoi(ptRoi);
					roiMgr.rename(i, "Path2D_" + (i+1) + "Slice_" + (zData[j]+1));
					j++;
					i++;
				}
			}
		}
	}
}
