package CT_Tools;

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
import java.util.Vector;

import jhd.Projection.*;



public class MuLinSlice_To_Fan_Sinogram implements PlugInFilter, DialogListener {

	ImagePlus imp;
	static final String myDialogTitle = "Fan Beam CTscan";
	
	boolean scale16,padImage;
	int oldImageWidth,oldImageHeight;

	
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
		oldImageWidth =ip.getWidth();
		oldImageHeight =ip.getHeight();
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

	@SuppressWarnings("unchecked")
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
		gd.addMessage("This plugin scans linear attenuation\n images to sinograms.",myFont,Color.BLACK);
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
		gd.addHelp("https://lazzyizzi.github.io/CTsimulator.html");
		
		Vector<TextField> numFlds = gd.getNumericFields();
		numFlds.get(0).setName("numAng");
		numFlds.get(1).setName("srcToDetCM");
		numFlds.get(2).setName("magnification");
		
		Vector<Checkbox> ckBoxes = gd.getCheckboxes();
		ckBoxes.get(0).setName("padImage");
		ckBoxes.get(1).setName("scale16");
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
			ImagePlus sinoImp = IJ.createImage("sino", sinoWidth, fpSet.numAng, 1, 32);
			String[] props = new String[8];
			props[0]="Geometry"; 
			props[1]="Parallel";
			props[2]="Source";
			props[3]="Tau Values";			
			props[4]="Source To Detector";
			props[5]=Double.toString(fpSet.srcToDetCM);			
			props[6]="Magnification";
			props[7]=Double.toString(fpSet.magnification);			
			sinoImp.setProperties(props);
			
			ImageProcessor sinoIP = sinoImp.getProcessor();
			sinoIP.setPixels(sinogram);
			
			// append "FanSino" to the image name
            String title;
			String name = imp.getTitle();
            int dotIndex = name.lastIndexOf(".");
            if(dotIndex != -1) title = name.substring(0, dotIndex);
            else title  = name;
            	
 			sinoImp.setTitle(title + "_Mag" + fpSet.magnification + "FanSino" + fpSet.numAng);
            
			// Set the sinogram X,Y units
			//The pixel values are in per pixel units
            Calibration sinoCal = sinoImp.getCalibration();
            sinoCal.setXUnit(unit);
            sinoCal.setYUnit("Deg");
            sinoCal.pixelWidth = pixSize;
            sinoCal.pixelHeight = 360.0/fpSet.numAng;
            
			sinoImp.show();
			
			//To reduce storage requirements, my reconstruction program takes 16-bit images of
			//Tau * 6000.  The statistical nature of x-ray counting usually limits accuracy
			//to 16-bits or less, so why should we use a 32-bit float to store a 16-bit number.
			if(scale16)
			{
				IJ.run(sinoImp, "Multiply...", "value=6000");
				ImageConverter.setDoScaling(false);
				IJ.run(sinoImp, "16-bit", "");
			}					
			IJ.run("Enhance Contrast", "saturated=0.35");	
		}
	}
	
	//*******************************************************************************

	//Simple Fan projection
	@SuppressWarnings("unchecked")
	private void GetSelections(GenericDialog gd)
	{
		String str;

		Vector<TextField> numFlds = gd.getNumericFields();
		str = numFlds.get(0).getText();
		if(isNumeric(str)) 	fpSet.numAng =  Integer.valueOf(str);

		str = numFlds.get(1).getText();
		if(isNumeric(str)) 	fpSet.srcToDetCM =  Float.valueOf(str);

		str = numFlds.get(2).getText();
		if(isNumeric(str)) 	fpSet.magnification =  Float.valueOf(str);

		Vector<Checkbox> ckBoxes = gd.getCheckboxes();
		padImage = ckBoxes.get(0).getState();		
		scale16 = ckBoxes.get(1).getState();
	}
	public static boolean isNumeric(String str)
	{ 
		try
		{  
			Double.parseDouble(str);  
			return true;
		}
		catch(NumberFormatException e)
		{  
			return false;  
		}  
	}
	
	//*******************************************************************************

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{

/*		for(int i=0;i< gd.getComponentCount();i++)
		{
			IJ.log("Component[" + i + "]=" + gd.getComponent(i).toString());			
		}
*/
		if(e!=null)
		{
			int index;
			Label label;
			GetSelections(gd);
			@SuppressWarnings("unchecked")
			Vector<TextField> numFldVec = gd.getNumericFields();
			//Vector<TextField> txtFldVec = gd.getStringFields();
			
			if(e.getSource() instanceof Checkbox)
			{
				Checkbox cb = (Checkbox) e.getSource();
				switch(cb.getName())
				{
				case "padImage":
					if(padImage)
					{
						int newWH = (int) (Math.ceil(Math.sqrt(oldImageWidth*oldImageWidth + oldImageHeight*oldImageHeight)));
						IJ.run("Canvas Size...", "width=" + newWH + " height="+newWH + " position=Center zero");
					}
					else
					{
						IJ.run("Canvas Size...", "width=" + oldImageWidth + " height="+oldImageHeight + " position=Center zero");				
					}
					
					double srcToSampCM = fpSet.srcToDetCM/fpSet.magnification;
					double sampToDetCM = fpSet.srcToDetCM - srcToSampCM;
					int detPixCnt= (int)(imp.getWidth()*fpSet.magnification);
					int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));
					
					index = GetLabelIndex(gd,"Axis to Detector");
					label = (Label) gd.getComponent(index);
					label.setText("Axis to Detector = " + String.format("%.3f" + " cm", sampToDetCM));
					
					index = GetLabelIndex(gd,"Detector Pixel");
					label = (Label) gd.getComponent(index);
					label.setText("Detector Pixel Count = " + detPixCnt);
						
					index = GetTextFieldIndex(numFldVec,"numAng");
					numFldVec.get(index).setText(String.valueOf(numAngles));
					break;
				case "scale16":
					break;
				}
				
			}
			else if(e.getSource() instanceof TextField)
			{
				int magIndex = GetTextFieldIndex(numFldVec,"magnification");
				int srcToDetIndex = GetTextFieldIndex(numFldVec,"srcToDetCM");
				if(e.getSource().equals(numFldVec.get(magIndex)) ||
						e.getSource().equals(numFldVec.get(srcToDetIndex)))//magnification or srcToDet
				{
					double srcToSampCM = fpSet.srcToDetCM/fpSet.magnification;
					double sampToDetCM = fpSet.srcToDetCM - srcToSampCM;
					int detPixCnt= (int)(imp.getWidth()*fpSet.magnification);
					int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));
					
					index = GetLabelIndex(gd,"Axis to Detector");
					label = (Label) gd.getComponent(index);
					label.setText("Axis to Detector = " + String.format("%.3f" + " cm", sampToDetCM));
					
					index = GetLabelIndex(gd,"Detector Pixel");
					label = (Label) gd.getComponent(index);
					label.setText("Detector Pixel Count = " + detPixCnt);

					index = GetTextFieldIndex(numFldVec,"numAng");
					numFldVec.get(index).setText(String.valueOf(numAngles));
				}
			}			
		}
//		GetSelections(gd);
//		if(e!=null && gd.getTitle()==myDialogTitle)
//		{
//
//			if(e.getSource().equals(gd.getComponent(9)))//padImage
//			{
//				if(padImage)
//				{
//					int newWH = (int) (Math.ceil(Math.sqrt(oldW*oldW + oldH*oldH)));
//					IJ.run("Canvas Size...", "width=" + newWH + " height="+newWH + " position=Center zero");
//				}
//				else
//				{
//					IJ.run("Canvas Size...", "width=" + oldW + " height="+oldH + " position=Center zero");				
//				}
//				
//				double srcToSampCM = fpSet.srcToDetCM/fpSet.magnification;
//				double sampToDetCM = fpSet.srcToDetCM - srcToSampCM;
//				Label sampToDetLbl = (Label) gd.getComponent(7);
//				sampToDetLbl.setText("Axis To Detector = " + String.format("%.3f" + " cm", sampToDetCM));
//
//				int detPixCnt= (int)(imp.getWidth()*fpSet.magnification);
//				int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));
//
//				Label detPixLbl = (Label) gd.getComponent(8);
//				detPixLbl.setText("Detector Pixels = " + detPixCnt);
//
//				TextField nAnglesTxt = (TextField) gd.getComponent(2);
//				nAnglesTxt.setText(String.valueOf(numAngles));			
//			}
//			
//			
//			if(e.getSource().equals(gd.getComponent(6)) ||
//			   e.getSource().equals(gd.getComponent(4))	)//magnification or srcToDet
//			{
//				double srcToSampCM = fpSet.srcToDetCM/fpSet.magnification;
//				double sampToDetCM = fpSet.srcToDetCM - srcToSampCM;
//				Label sampToDetLbl = (Label) gd.getComponent(7);
//				sampToDetLbl.setText("Axis To Detector = " + String.format("%.3f" + " cm", sampToDetCM));
//
//				int detPixCnt= (int)(imp.getWidth()*fpSet.magnification);
//				int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));
//
//				Label detPixLbl = (Label) gd.getComponent(8);
//				detPixLbl.setText("Detector Pixels = " + detPixCnt);
//
//				TextField nAnglesTxt = (TextField) gd.getComponent(2);
//				nAnglesTxt.setText(String.valueOf(numAngles));			
//			}			
//		}
		
		//ValidateParams(bfpSet); //can get annoying
		return true;
	}
	
	//*********************************************************************************/
	
	/**Java TextField should have a method like this!!
	 * @param textFieldVector A vector containing a generic dialog's text fields
	 * @param textFieldName The name of the field 
	 * @return the index of the field
	 */
	private int GetTextFieldIndex(Vector<TextField> textFieldVector, String textFieldName)
	{
		int index=-1, cnt = 0;
		for(TextField tf: textFieldVector)
		{
			String name = tf.getName();
			if(name.equals(textFieldName))
			{
				index = cnt;
				break;				
			}
			else cnt++;
		}
		return index;
	}
	
	//*********************************************************************************/

	/*There is no gd.getLabels() so we use the components directly*/
	private int GetLabelIndex(GenericDialog gd, String testText)
	{
		int index=-1;
		for(int i=0;i<gd.getComponentCount();i++)
		{
			if(gd.getComponent(i) instanceof Label)
			{	
				Label label = (Label) gd.getComponent(i);
				String lblTxt = label.getText();
				//IJ.log("index = " + i + " testText="  + testText + "   " + lblTxt);
				if(lblTxt.contains(testText))
				{
					index = i;
					break;
				}
			}
		}
		return index;
	}	
}
