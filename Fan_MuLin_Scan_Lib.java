//package Scanner_Sims;

/*
 * This plugin simulates a fan beam CT scan from 0 to 360 degrees.
 * The point source and detector array are rotated around the center of a square image.
 * The line integral of attenuation along a ray between the source and each detector element
 * is returned as a sinogram. 
 */

import ij.IJ;

import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;

//import java.awt.event.*;
import java.awt.*;
//import java.util.*;

import jhd.Projection.*;



public class Fan_MuLin_Scan_Lib implements PlugInFilter, DialogListener {

	ImagePlus imp;
	static final String myDialogTitle = "Fan Beam CTscan";
	
	boolean scale16,padImage;
	int oldW,oldH;

	
	//The class that does the simulation
	FanProjectors fanPrj = new FanProjectors();
	//The nested class containing the simulator's user supplied parameters
	FanProjectors.FanParams fpSet =  new FanProjectors.FanParams();
	
	//*******************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp)
	{
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
		String unit = imp.getCalibration().getUnit().toUpperCase();
		if(!unit.equals("CM"))
		{
			IJ.error("Input image pixel units must be in cm (centimeters)");
			return;
		}

		if(imp.getWidth() != imp.getHeight())
		{
			IJ.showMessage("Image must be Square. Check the PadImage Box in the next dialog");
		}

		if(doMyDialog())
		{
			if(validateParams(fpSet))
			{
				DoRoutine(fpSet);
				}
		}
	}
		
	//*******************************************************************************

	private boolean doMyDialog()
	{
		fpSet.srcToDetCM = 100;
		fpSet.magnification = 1.5f;
		float srcToSampCM = fpSet.srcToDetCM/fpSet.magnification;
		int detPixCnt= (int)(imp.getWidth()*fpSet.magnification);
		
		String dir = IJ.getDirectory("plugins");
		dir= dir.replace("\\","/");
		//String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";

		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);

		GenericDialog gd = new GenericDialog(myDialogTitle);
		gd.setInsets(10,0,0);
		gd.addMessage("360 degree Scan______________",myFont,Color.BLACK);
		int numAngles = (int) (Math.ceil(imp.getWidth()*Math.PI));
		gd.addNumericField("Suggested View Angles:", numAngles);
		gd.addNumericField("Source to Detector(cm):", fpSet.srcToDetCM);
		gd.addNumericField("Magnification:", fpSet.magnification);
		
		gd.addMessage("Axis to Detector = " + String.format("%.3f" + " cm", fpSet.srcToDetCM - srcToSampCM ));
		gd.addMessage("Detector Pixels = " + detPixCnt);
		gd.addDialogListener(this);
		gd.addCheckbox("Pad Image", false);		
		gd.addCheckbox("Scale to 16-bit proj", false);
		
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

	private boolean validateParams(FanProjectors.FanParams fpSet)
	{
		if(fpSet.magnification <=1)
		{
			IJ.error("Fan beam magnification must always be greater than 1");
			return false;			
		}
		if(fpSet.numAng> 1 && imp.getWidth()==imp.getHeight())
		{
			return true;
		}
		else
		{
			IJ.error("Image To Sinogram Error", "Images must be square and have 1 or more view angles");
			return false;
		}
	}
	
	//*******************************************************************************

	private void DoRoutine(FanProjectors.FanParams fpSet)
	{
		ImageProcessor ip = imp.getProcessor();
		Object image = ip.getPixels();
		
		if(image instanceof float[])
		{
			fpSet.pixSizeCM = (float)imp.getCalibration().pixelWidth;

			//Run the Fan Beam scan
			//long startTime = System.nanoTime();
			
			float[] sinogram = fanPrj.imageToFanBeamSinogram((float[])image,ip.getWidth(), ip.getHeight(), fpSet,true);
			//float[] sinogram = fanPrj.imageToFanBeamSinogram((float[])image,ip.getWidth(), ip.getHeight(),
			//		(float)fpSet.pixSizeCM,fpSet.srcToDetCM,fpSet.magnification,fpSet.numAng ,true);
			
			//long endTime = System.nanoTime();
			//long duration = (endTime - startTime);
			//IJ.log("Execution time=" + duration + "nSec");

			
			//Correction for pixel size must be done by this calling function
			//Otherwise the units are per pixel
			Calibration  imgCal = imp.getCalibration();		
			String unit = imgCal.getUnit();	// bark if not "cm" ?
			double pixSize = imgCal.getX(1); //cm per pixel
			for(int i=0;i<sinogram.length;i++)
			{
				sinogram[i] *= pixSize;
			}
			int sinoWidth = (int)(ip.getWidth()*fpSet.magnification);
			//Create an image to display the results
			ImagePlus sino = IJ.createImage("sino", sinoWidth, fpSet.numAng, 1, 32);
			sino.setProp("Source To Detector" , fpSet.srcToDetCM + " pixels");
			sino.setProp("Source To Sample" , fpSet.srcToDetCM/fpSet.magnification + " pixels");
			sino.setProp("Magnification" , fpSet.magnification);
			sino.setProp("Fan Beam Sinogram" , "______________");
			
			ImageProcessor sinoIP = sino.getProcessor();
			sinoIP.setPixels(sinogram);
			
			// append "FanSino" to the image name
            String title;
			String name = imp.getTitle();
            int dotIndex = name.lastIndexOf(".");
            if(dotIndex != -1) title = name.substring(0, dotIndex);
            else title  = name;
            	
 			sino.setTitle(title + "_Mag" + fpSet.magnification + "FanSino" + fpSet.numAng);
            
			// Set the sinogram X,Y units
			//The pixel values are in per pixel units
            Calibration sinoCal = sino.getCalibration();
            sinoCal.setXUnit(unit);
            sinoCal.setYUnit("Deg");
            sinoCal.pixelWidth = pixSize;
            sinoCal.pixelHeight = 360.0/fpSet.numAng;
            
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
	}
	
	//*******************************************************************************

	//Simple Fan projection
	private void GetSelections(GenericDialog gd)
	{
		fpSet.numAng = (int)gd.getNextNumber();
		fpSet.srcToDetCM = (float)gd.getNextNumber();
		fpSet.magnification = (float)gd.getNextNumber();
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

			if(theEvent.getSource().equals(gd.getComponent(9)))//padImage
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
				
				double srcToSampCM = fpSet.srcToDetCM/fpSet.magnification;
				double sampToDetCM = fpSet.srcToDetCM - srcToSampCM;
				Label sampToDetLbl = (Label) gd.getComponent(7);
				sampToDetLbl.setText("Axis To Detector = " + String.format("%.3f" + " cm", sampToDetCM));

				int detPixCnt= (int)(imp.getWidth()*fpSet.magnification);
				int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));

				Label detPixLbl = (Label) gd.getComponent(8);
				detPixLbl.setText("Detector Pixels = " + detPixCnt);

				TextField nAnglesTxt = (TextField) gd.getComponent(2);
				nAnglesTxt.setText(String.valueOf(numAngles));			
			}
			
			
			if(theEvent.getSource().equals(gd.getComponent(6)) ||
			   theEvent.getSource().equals(gd.getComponent(4))	)//magnification or srcToDet
			{
				double srcToSampCM = fpSet.srcToDetCM/fpSet.magnification;
				double sampToDetCM = fpSet.srcToDetCM - srcToSampCM;
				Label sampToDetLbl = (Label) gd.getComponent(7);
				sampToDetLbl.setText("Axis To Detector = " + String.format("%.3f" + " cm", sampToDetCM));

				int detPixCnt= (int)(imp.getWidth()*fpSet.magnification);
				int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));

				Label detPixLbl = (Label) gd.getComponent(8);
				detPixLbl.setText("Detector Pixels = " + detPixCnt);

				TextField nAnglesTxt = (TextField) gd.getComponent(2);
				nAnglesTxt.setText(String.valueOf(numAngles));			
			}			
		}
		
		//ValidateParams(bfpSet); //can get annoying
		return true;
	}
}
