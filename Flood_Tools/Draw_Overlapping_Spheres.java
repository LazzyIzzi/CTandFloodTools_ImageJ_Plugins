package Flood_Tools;

import ij.*;
import ij.ImagePlus;
import ij.gui.*;
import ij.measure.*;
import ij.plugin.filter.*;
import ij.process.*;

//import jhd.DistanceMaps.libJ8.EuclideanSpheres;
import jhd.FloodFill.EuclideanSpheres;

public class Draw_Overlapping_Spheres implements PlugInFilter {

	ImagePlus imp;

	//******************************************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_ALL+STACK_REQUIRED;		
	}

	//******************************************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{		
		GenericDialog gd = new GenericDialog("Draw Spheres Random Points");
		gd.addNumericField("Volume Fraction of Spheres", .1, 3);
		gd.addNumericField("Min Radius in user units", 2, 3);
		gd.addNumericField("Max Radius in user units", 10, 3);
		gd.addNumericField("Fill Sphere with value", 255, 3);
		gd.addHelp("https://lazzyizzi.github.io/EuclideanSpheres.html");
		gd.showDialog();

		if(gd.wasCanceled()) return;
				
		EuclideanSpheres.RandomSphereParams rsp = new EuclideanSpheres.RandomSphereParams();
		EuclideanSpheres myES = new EuclideanSpheres();
		Calibration ce = imp.getCalibration();
		ImageStack stack = imp.getStack();
		
		// get user parameters from dialog
		rsp.volFrac		= gd.getNextNumber();
		rsp.minRadius	= gd.getNextNumber();
		rsp.maxRadius	= gd.getNextNumber();
		rsp.fillVal		= gd.getNextNumber();		
		rsp.pixWidth	= ce.pixelWidth;
		rsp.pixHeight	= ce.pixelHeight;
		rsp.pixDepth	= ce.pixelDepth;	
		int imgWidth	= stack.getWidth();
		int imgHeight	= stack.getHeight();
		int imgDepth	= stack.size();
		Object[] data	= stack.getImageArray();

		if(rsp.volFrac>=1.0)
		{
			IJ.showMessage("Volume fraction must be less than 1.0");
			return;
		}
		int pixcount = myES.DrawRandomSpheres(data,imgWidth,imgHeight,imgDepth, rsp);
		IJ.showStatus("Voxels filled="+pixcount);		
	}
}

