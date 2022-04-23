package CT_Tools;

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
import java.util.Vector;

import jhd.Projection.*;
//import CT_Tools.Calc_Sinogram_Properties;

public class MuLinSlice_To_Parallel_Sinogram implements PlugInFilter, DialogListener {

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

	@SuppressWarnings("unchecked")
	private boolean doMyDialog()
	{
		String dir = IJ.getDirectory("plugins");
		dir= dir.replace("\\","/");
		//String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";

		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
		int detPixCnt= imp.getWidth();

		GenericDialog gd = new GenericDialog(myDialogTitle);
		gd.setInsets(10,0,0);
		gd.addMessage("This plugin scans linear attenuation\n images to sinograms.",myFont,Color.BLACK);
		gd.setInsets(10,0,0);
		gd.addMessage("180 degree Scan______________",myFont,Color.BLACK);
		int numAngles = (int) (imp.getWidth()*1.570796327);
		gd.addNumericField("Suggested View Angles", numAngles);
		gd.setInsets(0,0,0);
		gd.addMessage("Detector Pixels = " + detPixCnt);
		gd.addCheckbox("Pad Image", false);
		gd.addCheckbox("Scale to 16-bit", false);
		gd.addDialogListener(this);
		gd.addHelp("https://lazzyizzi.github.io/CTsimulator.html");
		
		Vector<TextField> numFlds = gd.getNumericFields();
		numFlds.get(0).setName("numAng");
		
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
			ImagePlus sinoImp = IJ.createImage("sino", ip.getWidth(), mppSet.numAng, 1, 32);
			//Calc_Sinogram_Properties myProps = new Calc_Sinogram_Properties();
			//sinoImp.setProp(myProps.tags[0], "Geometry");
			String[] props = new String[4];
			props[0]="Geometry"; 
			props[1]="Parallel";
			props[2]="Source";
			props[3]="Tau Values";			
			sinoImp.setProperties(props);

			ImageProcessor sinoIP = sinoImp.getProcessor();
			sinoIP.setPixels(sinogram);

			// append "ParSino" to the image name
			String title;
			String name = imp.getTitle();
			int dotIndex = name.lastIndexOf(".");
			if(dotIndex != -1) title = name.substring(0, dotIndex);
			else title  = name;
			sinoImp.setTitle(title + "_ParSino");// + mppSet.numAng);

			// Set the sinogram X,Y units
			//The pixel values are in per pixel units
			Calibration sinoCal = sinoImp.getCalibration();
			sinoCal.setXUnit(unit);
			sinoCal.setYUnit("Deg");
			sinoCal.pixelWidth = pixSize;
			sinoCal.pixelHeight = 180.0/mppSet.numAng;

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

	@SuppressWarnings("unchecked")
	private void GetSelections(GenericDialog gd)
	{
		String str;

		Vector<TextField> numFlds = gd.getNumericFields();
		str = numFlds.get(0).getText();
		if(isNumeric(str)) 	mppSet.numAng =  Integer.valueOf(str);

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
	GetSelections(gd);
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
					int newWH = (int) (Math.ceil(Math.sqrt(oldW*oldW + oldH*oldH)));
					IJ.run("Canvas Size...", "width=" + newWH + " height="+newWH + " position=Center zero");
				}
				else
				{
					IJ.run("Canvas Size...", "width=" + oldW + " height="+oldH + " position=Center zero");				
				}
				int detPixCnt= (int)(imp.getWidth());
			
				int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));
				
				index = GetLabelIndex(gd,"Detector Pixel");
				label = (Label) gd.getComponent(index);
				label.setText("Detector Pixels = " + detPixCnt);
					
				index = GetTextFieldIndex(numFldVec,"numAng");
				numFldVec.get(index).setText(String.valueOf(numAngles));
				break;
			case "scale16":
				break;
			}
			
		}
//		else if(e.getSource() instanceof TextField)
//		{
//			int magIndex = GetTextFieldIndex(numFldVec,"magnification");
//			int srcToDetIndex = GetTextFieldIndex(numFldVec,"srcToDetCM");
//			if(e.getSource().equals(numFldVec.get(magIndex)) ||
//					e.getSource().equals(numFldVec.get(srcToDetIndex)))//magnification or srcToDet
//			{
//				int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));
//				
//	
//				index = GetTextFieldIndex(numFldVec,"numAng");
//				numFldVec.get(index).setText(String.valueOf(numAngles));
//			}
//		}			
	}		return true;
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
