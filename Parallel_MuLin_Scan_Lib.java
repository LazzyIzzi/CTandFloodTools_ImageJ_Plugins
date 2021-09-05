//package Scanner_Sims;

/*
 * This plugin simulates a parallel beam CT scan from 0 to 180 degrees.
 * The source and detector arrays are rotated around the center of a square image.
 * The line integral of attenuation along a ray between source-detector pairs
 * is returned as a sinogram. 
 */

import ij.IJ;

import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;

import java.awt.*;

import jhd.Projection.*;

public class Parallel_MuLin_Scan_Lib implements PlugInFilter, DialogListener {

	ImagePlus imp;
	static final String myDialogTitle = "Parallel Beam CTscan";
	boolean scale16,padImage;
	int oldW,oldH;
	
	ParallelProjectors parPrj = new ParallelProjectors();
	ParallelProjectors.ParallelParams mppSet = new ParallelProjectors.ParallelParams();
		
	//*******************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp) {
		// TODO Auto-generated method stub
		this.imp = imp;
		return DOES_32;
	}

	//*******************************************************************************

	@Override
	public void run(ImageProcessor ip)
	{
		//the original image width and height
		oldW =ip.getWidth();
		oldH =ip.getHeight();
		if(imp.getWidth() != imp.getHeight())
		{
			IJ.showMessage("Image must be Square. Check the PadImage Box in the next dialog");
		}
		
		if(doMyDialog())
		{
			if(validateParams(mppSet))
			{
				DoRoutine(mppSet);
			}
		}
	}


	//*******************************************************************************

	private boolean doMyDialog()
	{
		String dir = IJ.getDirectory("plugins");
		dir= dir.replace("\\","/");
		//String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";

		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
		int detPixCnt= imp.getWidth();

		GenericDialog gd = new GenericDialog(myDialogTitle);
		gd.setInsets(10,0,0);
		gd.addMessage("180 degree Scan______________",myFont,Color.BLACK);
		int numAngles = (int) (imp.getWidth()*1.570796327);
		gd.addNumericField("Suggested View Angles", numAngles);
		gd.setInsets(0,0,0);
		gd.addMessage("Detector Pixel Count = " + detPixCnt);
		gd.addCheckbox("Pad Image", false);
		gd.addCheckbox("Scale to 16-bit", false);
		gd.addDialogListener(this);
		
		gd.showDialog();

		if (gd.wasCanceled())
		{
			return false;
		}
		else
		{
			GetSelections(gd);
			return true;
		}
	
	}
	
	//*******************************************************************************

	private boolean validateParams(ParallelProjectors.ParallelParams mppSet)
	{
		if(mppSet.numAng> 1 && imp.getWidth()==imp.getHeight())
		return true;
		else
		{
			IJ.error("Image To Sinogram Error", "Images must be square and 1 or more angles");
			return false;
		}
	}
	
	//*******************************************************************************

private void DoRoutine(ParallelProjectors.ParallelParams mppSet)
{
	ImageProcessor ip = imp.getProcessor();
	Object image = ip.getPixels();
	
	if(image instanceof float[])
	{
		float[] sinogram = parPrj.imageToParallelSinogram((float[])image,ip.getWidth(),ip.getHeight(),mppSet.numAng);

		//Correction for pixel size must be done by this calling function
		//Otherwise the units are per pixel
		Calibration  imgCal = imp.getCalibration();		
		String unit = imgCal.getUnit();	// bark if not "cm" ?
		double pixSize = imgCal.getX(1); //cm per pixel
		for(int i=0;i<sinogram.length;i++)
		{
			sinogram[i] *= pixSize;
		}

		//Create an image to display the results
		ImagePlus sino = IJ.createImage("sino", ip.getWidth(), mppSet.numAng, 1, 32);
		sino.setProp("Parallel Beam Sinogram" , "______________");
		
		ImageProcessor sinoIP = sino.getProcessor();
		sinoIP.setPixels(sinogram);
		
		// append "ParSino" to the image name
        String title;
		String name = imp.getTitle();
        int dotIndex = name.lastIndexOf(".");
        if(dotIndex != -1) title = name.substring(0, dotIndex);
        else title  = name;
		sino.setTitle(title + "_ParSino" + mppSet.numAng);
        
		// Set the sinogram X,Y units
		//The pixel values are in per pixel units
        Calibration sinoCal = sino.getCalibration();
        sinoCal.setXUnit(unit);
        sinoCal.setYUnit("Deg");
        sinoCal.pixelWidth = pixSize;
        sinoCal.pixelHeight = 180.0/mppSet.numAng;
        
		sino.show();
		
		//To reduce storage requirements, my reconstruction program takes 16-bit images of
		//Tau * 6000.  The statistical nature of x-ray counting usually limits accuracy
		//to 16-bits or less, so why should we use a 32-bit float to store a 16-bit number.
		if(scale16)
		{
			IJ.run(sino, "Multiply...", "value=6000");
			ImageConverter.setDoScaling(false);
			IJ.run(sino, "16-bit", "");
		}					
		IJ.run("Enhance Contrast", "saturated=0.35");	
	}
}	//*******************************************************************************

	private void GetSelections(GenericDialog gd)
	{
		mppSet.numAng = (int)gd.getNextNumber();
		padImage = gd.getNextBoolean();
		scale16 = gd.getNextBoolean();
	}
	
	//*******************************************************************************

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent theEvent)
	{
/*		for(int i=0;i< gd.getComponentCount();i++)
		{
			IJ.log("Component[" + i + "]=" + gd.getComponent(i).toString());			
		}
*/
	GetSelections(gd);
		if(theEvent!=null && gd.getTitle()==myDialogTitle)
		{

			if(theEvent.getSource().equals(gd.getComponent(4)))//padImage
			{
				if(padImage)
				{
					int newWH = (int) (Math.ceil(Math.sqrt(oldW*oldW + oldH*oldH)));
					IJ.run("Canvas Size...", "width=" + newWH + " height="+newWH + " position=Center zero");
				}
				else
				{
					IJ.run("Canvas Size...", "width=" + oldW + " height="+oldH + " position=Center zero");				
				}
				
				//imp.updateAndDraw();

				int detPixCnt= imp.getWidth();
				int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));

				Label detPixLbl = (Label) gd.getComponent(3);
				detPixLbl.setText("Detector Pixels = " + detPixCnt);

				TextField nAnglesTxt = (TextField) gd.getComponent(2);
				nAnglesTxt.setText(String.valueOf(numAngles));			
			}	
		}		
		return true;
	}

}
