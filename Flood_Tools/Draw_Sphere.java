package Flood_Tools;

import ij.*;
import ij.gui.*;
import ij.plugin.filter.*;
import ij.process.*;
import ij.measure.*;

//import jhd.DistanceMaps.libJ8.EuclideanSpheres;
//import jhd.DistanceMaps.libJ8.EuclideanSpheres.SphereParams;

import jhd.FloodFill.EuclideanSpheres;
import jhd.FloodFill.EuclideanSpheres.SphereParams;

public class Draw_Sphere implements PlugInFilter {

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
		GenericDialog gd = new GenericDialog("Draw Sphere");
		gd.addNumericField("x coordinate in pixels", 10, 3);
		gd.addNumericField("y coordinate in pixels", 10, 3);
		gd.addNumericField("z coordinate in pixels", 10, 3);
		gd.addNumericField("Radius in image units", 10, 3);
		gd.addNumericField("Fill Sphere with value", 255, 3);
		gd.addHelp("https://lazzyizzi.github.io/EuclideanSpheres.html");
		gd.showDialog();

		if(gd.wasCanceled()) return;
		
		EuclideanSpheres myES = new EuclideanSpheres();
		SphereParams sp = new SphereParams();
		Calibration ce = imp.getCalibration();

		sp.centerX= (int)gd.getNextNumber();
		sp.centerY= (int)gd.getNextNumber();
		sp.centerZ= (int)gd.getNextNumber();
		sp.radius = gd.getNextNumber();
		sp.fillVal = gd.getNextNumber();				
		sp.pixWidth = ce.pixelWidth;
		sp.pixHeight = ce.pixelHeight;
		sp.pixDepth = ce.pixelDepth;
		sp.unit = ce.getUnit();
		
		ImageStack stack = imp.getStack();
		int imgWidth = stack.getWidth();
		int imgHeight = stack.getHeight();
		int imgDepth = stack.size();
		Object[] data = stack.getImageArray();
		
		int pixcount = myES.DrawSphere(data, imgWidth, imgHeight, imgDepth,sp);
		IJ.showStatus("Voxels filled="+pixcount);		
	}
}

