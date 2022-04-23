package Flood_Tools;

/* The imageJ "Draw" plugin has most of this plugin's capability to draw ROIs on an image.  But it does not
 * allow drawing using floating point numbers, severely limiting pixel value choices.
 * Also, the points in an ROI polygon are not necessarily touching.
 * In this plugin the missing points between the ROI points list obtained from the roi.polygon are filled in using Bresenham's algorithm
 */

import java.awt.Polygon;
import java.util.ArrayList;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.*;
import ij.plugin.PlugIn;

//***********************************************************************************************************************

public class Write_ROI_On_Image implements PlugIn
{
	
	private class Point3D
	{
		public Point3D(int x, int y, int z)
		{
			super();
			this.x = x;
			this.y = y;
			this.z = z;
		}
//		public Point3D(int x, int y, int z, double val)
//		{
//			super();
//			this.x = x;
//			this.y = y;
//			this.z = z;
//			this.val = val;
//		}
		int x,y,z;
//		double val;
	}
	
	//***********************************************************************************************************************
		
	@Override
	public void run(String arg)
	{
		GenericDialog gd = new GenericDialog("Draw Roi on image");
		gd.addNumericField("Set Roi boundry to:", 128);
		gd.addCheckbox("Write in all slices", false);
		gd.showDialog();

		if(gd.wasOKed())
		{
			double val = gd.getNextNumber();
			boolean allSlices = gd.getNextBoolean();

			ImagePlus imp = IJ.getImage();
			ArrayList<Point3D> roiList = getRoiPixels(imp);
			ImageStack stack = imp.getStack();

			if(allSlices)
			{
				//Draws in all slices
				for(int z=0;z<imp.getStack().getSize();z++)
				{
					for(int i=0;i<roiList.size();i++)
					{
						stack.setVoxel(roiList.get(i).x, roiList.get(i).y, z, val);
					}
				}
			}
			else
			{
				//Draw in current roi slice
				for(int i=0;i<roiList.size();i++)
				{
					stack.setVoxel(roiList.get(i).x, roiList.get(i).y, roiList.get(i).z-1, val);
				}

			}

			imp.updateAndDraw();
			IJ.run(imp, "Enhance Contrast", "saturated=0.35");

		}
	}
	
	//***********************************************************************************************************************
	
	private ArrayList<Point3D> getRoiPixels(ImagePlus imp)
	{
		
		ImageStack stack = imp.getStack();
		Roi roi		= imp.getRoi();
		int width	= stack.getWidth();
		int height	= stack.getHeight();
		int depth	= stack.getSize();		
		ArrayList<Point3D> line, bigList= new ArrayList<Point3D>();		 
		int j;

		if(roi!=null)
		{
			//ImageJ ROIs are described by a list of pixels that are not necessarily connected.
			//The ROI points list is obtained from the roi.polygon and the linking pixels are filled in using Bresenham's algorithm
			int slice = imp.getSlice();
			Polygon p= roi.getPolygon();			
			
			switch(roi.getType())
			{
			case Roi.POLYLINE:	//POLYLINE=6 aka segmented line,  an open polygon
			case Roi.ANGLE:		//ANGLE=8 two line segments joined at a common point, an open polygon
			case Roi.LINE:		//LINE=5 a straight line also an arrow line, an open polygon
			case Roi.FREELINE:	//FREELINE=7 an open freehand line
				for(j =0;j<p.npoints-1;j++)
				{
					line = bresenham3D(width,height,depth,p.xpoints[j],p.ypoints[j],slice,p.xpoints[j+1],p.ypoints[j+1],slice);
					bigList.addAll(line);
				}
				break;

			case Roi.POLYGON:	//POLYGON=2 closed polygon
			case Roi.RECTANGLE:	//RECTANGLE=0 closed polygon, also rounded rectangle
				for( j =0;j<p.npoints-1;j++)
				{
					line = bresenham3D(width,height,depth,p.xpoints[j],p.ypoints[j],slice,p.xpoints[j+1],p.ypoints[j+1],slice);
					bigList.addAll(line);
				}
				//Close the figure
				line = bresenham3D(width,height,depth,p.xpoints[j],p.ypoints[j],slice,p.xpoints[0],p.ypoints[0],slice);
				bigList.addAll(line);
				break;

			case Roi.FREEROI:	//FREEROI=3  closed freehand also an ellipse
			case Roi.TRACED_ROI://TRACED_ROI=4 closed the wand tool
			case Roi.OVAL:		//OVAL=1  closed
			case Roi.COMPOSITE:	//COMPOSITE=9 always closed. modify freehand ROI i.e. with selection brush tool
				for(j=0;j<p.npoints-1;j++)
				{
					line = bresenham3D(width,height,depth,p.xpoints[j],p.ypoints[j],slice,p.xpoints[j+1],p.ypoints[j+1],slice);
					bigList.addAll(line);
				}
				//Close the figure
				line = bresenham3D(width,height,depth,p.xpoints[j],p.ypoints[j],slice,p.xpoints[0],p.ypoints[0],slice);
				bigList.addAll(line);
				break;

			case Roi.POINT:	//POINT=10; 
				//Points are the only 3D roi
				//A bit clunky that we need to obtain the point's z coordinate from another class
				PointRoi pRoi = (PointRoi) roi;
				for(j =0;j<p.npoints;j++)
				{
					bigList.add(new Point3D(p.xpoints[j],p.ypoints[j],pRoi.getPointPosition(j)));
				}
				break;

			default:
				IJ.log("RoiType=" +roi.getType() + " not supported");
				IJ.log("RoiName=" +roi.getTypeAsString() + " not supported");
				break;
			}
		}
		return bigList;
	}
	
	//***********************************************************************************************

	/**Bresenham's line drawing algorithm is used to get the voxel locations and values between pairs of points
	 * @param x1 X, width, i position of line Start
	 * @param y1 Y, height, j position of  line Start
	 * @param z1 Z, depth, k position of  line Start
	 * @param x2 H, height, i position of  line End
	 * @param y2 Y, height, j position of  line End
	 * @param z2 Z, depth, k position of  line End
	 * @return An ArrayList of Point3D coordinates and values along a pixellated straight line between x1,y1,z1 and x2,y2,z2
	 */
	private ArrayList<Point3D> bresenham3D( int imgWidth, int imgHeight, int imgDepth, int x1, int y1, int z1, int x2, int y2, int z2)
	{
		int		i;
		int		dx, dy, dz;
		int		dx2, dy2, dz2;
		int		x_inc, y_inc, z_inc;
		int		l, m, n;
		int		err_1, err_2;
		int		newX,newY,newZ;
		ArrayList<Point3D> pointList = new ArrayList<Point3D>();
	
		// Check coordinates are within stack
		if (x1 < 0 || x1 > imgWidth) return null;
		if (y1 < 0 || y1 > imgHeight) return null;
		if (z1 < 0 || z1 > imgDepth) return null;
		if (x2 < 0 || x2 > imgWidth) return null;
		if (y2 < 0 || y2 > imgHeight) return null;
		if (z2 < 0 || z2 > imgDepth) return null;
	
		newX	= x1;
		newY	= y1;
		newZ	= z1;
		dx	= x2 - x1;
		dy	= y2 - y1;
		dz	= z2 - z1;
		x_inc	= (dx < 0) ? -1 : 1;
		l	= Math.abs(dx);
		y_inc	= (dy < 0) ? -1 : 1;
		m	= Math.abs(dy);
		z_inc	= (dz < 0) ? -1 : 1;
		n	= Math.abs(dz);
		dx2	= l << 1;
		dy2	= m << 1;
		dz2	= n << 1;

		if ((l >= m) && (l >= n))
		{
			err_1 = dy2 - l;
			err_2 = dz2 - l;
			for (i = 0; i < l; i++)
			{
				//pointList.add(new Point3D(newX,newY,newZ,stack.getVoxel(newX,newY,newZ)));
				pointList.add(new Point3D(newX,newY,newZ));
			
				if (err_1 > 0)
				{
					newY += y_inc;
					err_1 -= dx2;
				}
				if (err_2 > 0)
				{
					newZ += z_inc;
					err_2 -= dx2;
				}
				err_1 += dy2;
				err_2 += dz2;
		  	 	newX += x_inc;
			}
		}
		else if ((m >= l) && (m >= n))
		{
			err_1 = dx2 - m;
			err_2 = dz2 - m;
			for (i = 0; i < m; i++)
			{
				//pointList.add(new Point3D(newX,newY,newZ,stack.getVoxel(newX,newY,newZ)));
				pointList.add(new Point3D(newX,newY,newZ));
			
				if (err_1 > 0)
				{
			   		newX += x_inc;
					err_1 -= dy2;
				}
				if (err_2 > 0)
				{
					newZ += z_inc;
					err_2 -= dy2;
				}
				err_1 += dx2;
				err_2 += dz2;
				newY += y_inc;
			}
		}
		else
		{
			err_1 = dy2 - n;
			err_2 = dx2 - n;
			for (i = 0; i < n; i++)
			{
				//pointList.add(new Point3D(newX,newY,newZ,stack.getVoxel(newX,newY,newZ)));
				pointList.add(new Point3D(newX,newY,newZ));
			
				if (err_1 > 0)
				{
					newY += y_inc;
					err_1 -= dz2;
				}
				if (err_2 > 0)
				{
			  		newX += x_inc;
					err_2 -= dz2;
				}
				err_1 += dy2;
				err_2 += dx2;
				newZ += z_inc;
			}
		}
		return pointList;
	}	
}
