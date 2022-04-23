package Flood_Tools;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import jhd.FloodFill.*;

public class PorosityMap_To_HybridMap implements PlugIn {

	@Override
	public void run(String arg)
	{
		ImagePlus imp = IJ.getImage();
		ImageStack stk= imp.getStack();
		Calibration cal = imp.getCalibration();
		Object[] oImagArr = stk.getImageArray();				
		HybridFloodFill hff= new HybridFloodFill();
		
		double floodMax = hff.phiMapToHybridMap(oImagArr, stk.getWidth(), stk.getHeight(), stk.getSize(), cal.pixelWidth, cal.pixelHeight, cal.pixelDepth);
		
		imp.setDisplayRange(0, floodMax);
		imp.updateAndDraw();

	}

}
