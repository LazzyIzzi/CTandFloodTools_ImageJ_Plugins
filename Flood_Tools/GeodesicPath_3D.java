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


import jhd.FloodFill.Offsets.PointDesc;
import jhd.FloodFill.GDT3D;

/**
 * Plugin to draw in overlay the shortest path between the origin and a point in a 2D geodesic distance image.
 * @author John H Dunsmuir
 */
public class GeodesicPath_3D implements PlugInFilter {

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

		this.ip = ip;

		Properties props = imp.getImageProperties();
		if(props==null)
		{
			IJ.showMessage("Missing Image Properties","Please select a 3D Geodesic Transform Image");
			return;
		}
		else
		{
			String source = props.getProperty("GeodesicSource");
			String type = props.getProperty("GeodesicType");
			if(source!=null && type!=null)
			{
				if(source.indexOf("3D")==-1 || type.indexOf("Distance")==-1)
				{
					IJ.showMessage("Incorrect Image Properties","Please select a 3D Geodesic Transform Image");
					return;
				}
			}
			else
			{
				IJ.showMessage("Missing Image Properties","Please select a 3D Geodesic Transform Image");
				return;
			}
		}

		Roi roi = imp.getRoi();
		if(roi==null  || roi.getType() != Roi.POINT)
		{
			IJ.showMessage("Select a one or more points in the GDT area using the multi-Point ROI tool");
			return;
		}
		
		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
		GenericDialog gd = new GenericDialog("Geodesic Path 3D");
		gd.addMessage("Find the shortest path from a point\n"
				+ "in a 3D GDT image to the GDT origin.",myFont,Color.BLACK);
		gd.addCheckbox("Show Path(s) in New Blank Image", false);
		gd.setInsets(0, 20, 0);
		gd.addMessage("(Scroll through the Path Image or use\n"
				+ "Image->Stacks->3D Project to view.)");
		gd.setInsets(20, 20, 0);
		gd.addCheckbox("Write Path(s) to Roi Manager", true);
		gd.showDialog();
		
		if(gd.wasCanceled()) return;
		
		boolean showPaths = gd.getNextBoolean();
		boolean useRoiMgr =  gd.getNextBoolean();
		

		int width = imp.getWidth();
		int height = imp.getHeight();
		int depth = imp.getNSlices();
		ImageStack stack = imp.getStack();
		Object[] data = stack.getImageArray();
		ArrayList<PointDesc> probePts = new ArrayList<PointDesc>();
		Polygon p = roi.getPolygon();
		PointRoi pRoi=(PointRoi)roi;

		for(int j=0;j<p.npoints;j++)
		{
			//stack.getVoxel is 1 based, pRoi.getPointPosition is 0 based
			double val = stack.getVoxel(p.xpoints[j],p.ypoints[j],pRoi.getPointPosition(j)-1);
			// In the GDT the solid phase is -2, the unaccessed open phase is -1, and the seed points are 0
			if(val>0)
			{
				probePts.add(new PointDesc(p.xpoints[j],p.ypoints[j],pRoi.getPointPosition(j)-1,val));				
			}			
		}

		if(probePts.size()>0)
		{
//			GDT3D_V6 myGdt = new GDT3D_V6();
			GDT3D myGdt = new GDT3D();
			ArrayList<PointDesc>[] geoPaths3D = myGdt.getGeoPaths3D(data,width,height,depth, probePts);

			//Display the results
			if(geoPaths3D!=null)
			{
				if(useRoiMgr)
				{
					RoiManager roiMgr = RoiManager.getRoiManager();
					if(roiMgr==null)
					{
						roiMgr = new RoiManager();
					}
					int i=roiMgr.getCount();
					for(ArrayList<PointDesc> path : geoPaths3D)
					{
						PointRoi ptRoi = new PointRoi();
						ptRoi.setOptions("small");
						for(PointDesc point : path)
						{
							ptRoi.addPoint(point.x,point.y,point.z+1);
						}
						roiMgr.addRoi(ptRoi);
						roiMgr.rename(i, "Path3D_" + (i+1));
						i++;
					}				
				}

				if(showPaths)
				{
					ImagePlus pathImp = IJ.createImage("Path Image",width,height,depth,8);
					ImageStack pathStk = pathImp.getStack();

					int i=101;
					for(ArrayList<PointDesc> path : geoPaths3D)
					{
						for(PointDesc pl : path)
						{
							pathStk.setVoxel(pl.x,pl.y,pl.z, i);
						}
						i++;
						if(i>255) break;
					}
					pathImp.setDisplayRange(0, i);
					pathImp.show();
				}
			}
		}
	}
}
