package Flood_Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.PlugIn;

public class Set_Stack_Scale implements PlugIn {

	@Override
	public void run(String arg)
	{
		ImagePlus imp = IJ.getImage();
		Calibration cal = imp.getCalibration();
		
		//Calibration cal = new Calibration();
		cal = imp.getCalibration();
		
		GenericDialog gd = new GenericDialog("Stack Set Scale");
		gd.addNumericField("Horizontal (x) Size:", cal.pixelWidth);
		gd.addStringField("Horizontal (x) unit:", cal.getXUnit());
		gd.addNumericField("Vertical (y) Size:", cal.pixelHeight);
		gd.addStringField("Vertical (y) unit:", cal.getYUnit());
		gd.addNumericField("Depth (z) Size:", cal.pixelDepth);
		gd.addStringField("Depth (z) unit:", cal.getZUnit());
		gd.showDialog();
		
		if(gd.wasOKed())
		{
			cal.pixelWidth = gd.getNextNumber();
			cal.pixelHeight = gd.getNextNumber();
			cal.pixelDepth = gd.getNextNumber();
			cal.setXUnit(gd.getNextString());
			cal.setYUnit(gd.getNextString());
			cal.setZUnit(gd.getNextString());
		}
		
		imp.updateAndRepaintWindow();		
		
//		IJ.log("pixelWidth=" + cal.pixelWidth + " " + cal.getXUnit());
//		IJ.log("pixelHeight=" + cal.pixelHeight + " " + cal.getYUnit());
//		IJ.log("pixelDepth=" + cal.pixelDepth + " " + cal.getZUnit());
		
	}

}
