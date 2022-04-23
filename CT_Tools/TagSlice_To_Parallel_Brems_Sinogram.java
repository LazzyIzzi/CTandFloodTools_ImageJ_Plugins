package CT_Tools;

/*
 * This plugin simulates a parallel beam CT scan from 0 to 180 degrees
 * of a segmented 2D image using a conventional x-ray source and a scintillation
 * detector.
 * 
 * The spectral intensity of the source is estimated using the Kramers equation.
 * The transmission of the filter, the absorption of the image (sample) and the
 * absorption of the detector are computed using Beer-Lambert.
 * 
 * Polychromatic x-rays are simulated by combining the sample signals taken at
 * increments of x-ray energy.
 * The attenuation of each component is computed from the components formula 
 * and density and the x-ray energy.
 * 
 * A Ct scan is simulated by rotating and projecting the image to form a simogram.
 * 
 *  The Image:
 *  1. is segmented into N components. The pixel values of each component is assigned a number ID.
 *  2. for each
 */

import ij.IJ;

import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.gui.*;
import ij.measure.Calibration;

//import java.awt.event.*;
import java.io.File;
import java.awt.*;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Properties;

import jhd.MuMassCalculator.*;
import gray.AtomData.*;
import jhd.Projection.*;
import jhd.Serialize.Serializer;
import tagTools.common.MatlListTools2;


public class TagSlice_To_Parallel_Brems_Sinogram implements PlugInFilter , DialogListener
{
	//Used to test formulas prior to launching the simulator
	MuMassCalculator mmc = new MuMassCalculator();
	ParallelProjectors parPrj = new ParallelProjectors();
	//A serializable class containing the simulator's user supplied parameters
	ParallelProjectors.BremParallelParams bppSet = new  ParallelProjectors.BremParallelParams();
	
	//The class used to serialize (save) the users selections
	Serializer ser = new Serializer();
	//The class used to manage materials Lists
	MatlListTools2 mlt=new MatlListTools2();	
	//The nested class containing  materials list tag information
	MatlListTools2.TagSet tagSet;
	//Arrays to unpack TagData materials lists
	String[] matlArr;
	String[] formula;
	double[] gmPerCC;
	boolean scale16,padImage;

	GenericDialog gd = new GenericDialog(myDialogTitle);
	ImagePlus imp;
	
	String[] targetSymb = {"Ag","Au","Cr","Cu","Mo","Rh","W"};
	String[] filterSymb = {"Ag","Al","Cu","Er","Mo","Nb","Rh","Ta"};
	
	static final String myDialogTitle = "Polychromatic Parallel Beam CTscan";
	static final String mySettingsTitle = "Polychromatic_ParallelBeam_Params";
	
	//the full path to the dialog settings
	String dir = IJ.getDirectory("plugins");	
	String settingsPath = dir+ "DialogSettings" + File.separator + mySettingsTitle + ".ser";

	int oldImageWidth,oldImageHeight;
	
	//*******************************************************************************

	@Override
	public int setup(String arg, ImagePlus imp) {
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
			IJ.error("Pixel units must be in cm (centimeters)");
			return;
		}
		
		if(imp.getWidth() != imp.getHeight())
		{
			IJ.showMessage("Image must be Square. Check the PadImage Box in the next dialog");
		}

		String dir = IJ.getDirectory("plugins");
		String path = dir + "DialogData\\DefaultMaterials.csv";
		tagSet = mlt.loadTagFile(path);

		//Read the saved dialog settings
		bppSet = (ParallelProjectors.BremParallelParams)ser.ReadSerializedObject(settingsPath);
		if(bppSet==null)
		{
			bppSet = GetDialogDefaultSettings();
		}

		if(DoDialog())
		{
			if(ValidateParams(bppSet))
			{
				DoRoutine(bppSet);
				ser.SaveObjectAsSerialized(bppSet, settingsPath);
			}
		}
	}
	
	//*******************************************************************************

	private ParallelProjectors.BremParallelParams GetDialogDefaultSettings()
	{
		ParallelProjectors.BremParallelParams dlogSet = new ParallelProjectors.BremParallelParams();
		dlogSet.target = "W";
		dlogSet.kv = 160;
		dlogSet.ma = 100;
		dlogSet.nBins = 20;
		dlogSet.minKV = 20;

		dlogSet.filter = "Cu";
		dlogSet.filterCM = 0.1f;
		dlogSet.filterGmPerCC = 8.41;

		//Tagged Image
		dlogSet.pixSizeCM=.001f;		
		//convert Default tag data to arrays
		int[] tag = new int[tagSet.tagData.size()];
		String[] name =  new String[tagSet.tagData.size()];
		String[] formula =  new String[tagSet.tagData.size()];
		double[] gmPerCC =  new double[tagSet.tagData.size()];
		int i=0;
		for(MatlListTools2.TagData td : tagSet.tagData)
		{
			tag[i] = td.matlTag;
			name[i] = td.matlName;
			formula[i] = td.matlFormula;
			gmPerCC[i] = td.matlGmPerCC;
			i++;
		}

		dlogSet.matlTag=tag;
		dlogSet.matlName=name;
		dlogSet.matlFormula=formula;
		dlogSet.matlGmPerCC=gmPerCC;
		
		//CT params
		dlogSet.numAng=(int)(0.5*Math.PI*imp.getWidth());
		padImage=false;

		//Detector
		dlogSet.detFormula="Cs:1:I:1";
		dlogSet.detCM=.01;
		dlogSet.detGmPerCC=8.41;		
		scale16=false;
		
		return dlogSet;		
	}

	//*******************************************************************************

	@SuppressWarnings("unchecked")
	private boolean DoDialog()
	{
		//String dir = IJ.getDirectory("plugins");
		dir= dir.replace("\\","/");
		//String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";

		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);
		int detPixCnt= imp.getWidth();


		gd.addDialogListener(this);
		gd.setInsets(10,0,0);
		gd.addMessage("This plugin scans tagged images\nto bremsstrahlung sinograms.",myFont,Color.BLACK);
		//X-ray Source
		gd.setInsets(10,0,0);
		gd.addMessage("X-ray Source________________",myFont,Color.BLACK);
		gd.addChoice("Target",targetSymb,"W");
		gd.addNumericField("KV", bppSet.kv);
		gd.addNumericField("mA", bppSet.ma);
		gd.addNumericField("KeV Bins", bppSet.nBins);
		gd.addNumericField("Min KeV", bppSet.minKV);
		
		//Filter
		gd.setInsets(10,0,0);
		gd.addMessage("Source Filter________________",myFont,Color.BLACK);
		gd.addChoice("Material",filterSymb,bppSet.filter);
		gd.addNumericField("Thickness(cm)", bppSet.filterCM);
								
		//CT
		gd.setInsets(10,0,0);
		gd.addMessage("180 degree Scan______________",myFont,Color.BLACK);
		gd.addNumericField("Suggested View Angles", bppSet.numAng);
		gd.setInsets(0,0,0);
		gd.addMessage("Detector Pixel Count = " + detPixCnt);

		//Detector
		gd.setInsets(10,0,0);
		gd.addMessage("Detector___________________",myFont,Color.BLACK);
		gd.addStringField("Formula", bppSet.detFormula);
		gd.addNumericField("Thickness(cm)", bppSet.detCM);
		gd.addNumericField("Density(gm/cc)", bppSet.detGmPerCC);
		gd.addCheckbox("Scale to 16-bit proj", false);
		gd.addCheckbox("Pad Image", false);
		gd.addHelp("https://lazzyizzi.github.io/CTsimulator.html");
     	
		//Lock the names of the dialog components
		//so that they can be referred to by name.
		//Referring to the component by number is
		//hard to read and debug.
		Vector<Choice> choices = gd.getChoices();
		choices.get(0).setName("Target Choices");
		choices.get(1).setName("Filter Choices");
		
		Vector<TextField> txtFlds = gd.getStringFields();
		txtFlds.get(0).setName("Detector Formula");
		
		Vector<TextField> numFlds = gd.getNumericFields();
		numFlds.get(0).setName("kv");
		numFlds.get(1).setName("ma");
		numFlds.get(2).setName("nBins");
		numFlds.get(3).setName("minKV");
		numFlds.get(4).setName("filterCM");
		numFlds.get(5).setName("numAng");
		numFlds.get(6).setName("detCM");
		numFlds.get(7).setName("detGmPerCC");
		
		Vector<Checkbox> ckBoxes = gd.getCheckboxes();
		ckBoxes.get(0).setName("scale16");
		ckBoxes.get(1).setName("padImage");
		
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

	private boolean ValidateParams(ParallelProjectors.BremParallelParams bppSet)
	{
		//Test the formulas
		ArrayList<AtomData> formula;

		//Pre-screen the formulas for correctness
		formula = mmc.createFormulaList(bppSet.target);
		if(formula==null) {IJ.error(bppSet.target + " Is not a valid target material"); return false;}
		formula = mmc.createFormulaList(bppSet.filter);
		if(formula==null) {IJ.error(bppSet.filter + " Is not a valid filter material"); return false;}
		
		if(bppSet.matlFormula==null){IJ.error("Missing Formulas"); return false;}
		if(bppSet.matlGmPerCC==null){IJ.error("Missing Densities"); return false;}
		
		for(int i=1;i< bppSet.matlFormula.length;i++)
		{
			if(bppSet.matlGmPerCC[i] < 0){IJ.error("Material 1 Density " + bppSet.matlGmPerCC[i] + " Cannot be negative"); return false;}
			if(bppSet.matlFormula[i] == null){IJ.error("Missing Formula at item " + i); return false;}
			formula = mmc.createFormulaList(bppSet.matlFormula[i]);
			if(formula==null) {IJ.error(bppSet.matlFormula[i] + " Is not a valid  material"); return false;}			
		}
		
		formula = mmc.createFormulaList(bppSet.detFormula);
		if(formula==null) {IJ.error(bppSet.detFormula + " Is not a valid detector material"); return false;}
		
		//Test the numbers
		if(bppSet.kv < bppSet.minKV){IJ.error("Source KV " + bppSet.kv + " Must be greater than " + bppSet.minKV + "KV"); return false;}
		if(bppSet.kv <=0){IJ.error("Source KV " + bppSet.kv + " Must be greater than 0 KV"); return false;}
		if(bppSet.ma <=0){IJ.error("Source mA " + bppSet.ma + " Must be greater than 0 mA"); return false;}
		if(bppSet.nBins <=0){IJ.error("Bin Count " + bppSet.nBins + " Must be greater than 0"); return false;}
		if(bppSet.minKV > bppSet.kv){IJ.error("Source minMV " + bppSet.minKV + " Must be less than " + bppSet.kv + "KV"); return false;}
		
		if(bppSet.filterCM < 0){IJ.error("Filter Thickness " + bppSet.filterCM + " Cannot be negative"); return false;}
		if(bppSet.filterGmPerCC <= 0){IJ.error("Filter Density " + bppSet.filterGmPerCC + " Cannot be negative"); return false;}
		
		if(bppSet.numAng < 1){IJ.error("Number of angles " + bppSet.numAng + " Cannot be negative or zero"); return false;}
		if(bppSet.detCM <= 0){IJ.error("Detector Thickness " + bppSet.detCM + " Cannot be negative"); return false;}
		if(bppSet.detGmPerCC <= 0){IJ.error("Detector Densith " + bppSet.detCM + " Cannot be negative or zero"); return false;}
		
		return true;
	}
	
	//*******************************************************************************

	private void DoRoutine(ParallelProjectors.BremParallelParams bppSet)
	{
		int width = imp.getWidth();
		int height = imp.getHeight();

		ImageProcessor ip = imp.getProcessor();
		Object image = ip.getPixels();

		if(image instanceof float[])
		{	
			//Call the MuMassCalculator Projector library function
			//long startTime = System.nanoTime();
			float[] sino = parPrj.imageToBremsstrahlungParallelSinogram2(bppSet, (float [])image, width, height);
			//long endTime = System.nanoTime();
			//long duration = (endTime - startTime);
			//IJ.log("Execution time=" + duration + "nSec");
			
			// append "ParBremSino" and the angle count to the image name
            String title;
			String name = imp.getTitle();
            int dotIndex = name.lastIndexOf(".");
            if(dotIndex != -1) title = name.substring(0, dotIndex);
            else title  = name;
            title += "_ParBremSino";
            //title += "_KV(" + bppSet.kv +"-" + bppSet.minKV + ")";
            //title += bppSet.filter +"(" + bppSet.filterCM + ")";
            //title += bppSet.detFormula + "_" +String.format("%.2f" + "cm", bppSet.detCM);
 			ImagePlus sinoImp = IJ.createImage(title , imp.getWidth(), bppSet.numAng,1,32);
			
			//Record the scan conditions in the image properties Info
			//They get posted in strange order
			//int propCnt = 18 + 4*bppSet.matlGmPerCC.length;;		
			String[] props = new String[24];
			props[0]="Geometry"; 
			props[1]="Parallel";
			props[2]="Source";
			props[3]="Bremsstrahlung";			
			props[4]="Source KV";
			props[5]=Double.toString(bppSet.kv);
			props[6]="Source mA";
			props[7]=Double.toString(bppSet.ma);
			props[8]="Source Target";
			props[9]=bppSet.target;
			props[10]="Min keV";
			props[11]=Double.toString(bppSet.minKV);
			props[12]="Bins";
			props[13]=Double.toString(bppSet.nBins);
			props[14]="Filter";
			props[15]=bppSet.filter;
			props[16]="Filter(cm)";
			props[17]=Double.toString(bppSet.filterCM);
			props[18]="Detector";
			props[19]=bppSet.detFormula;
			props[20]="Detector(cm)";
			props[21]=Double.toString(bppSet.detCM);
			props[22]="Detector(gm/cc)";
			props[23]=Double.toString(bppSet.detGmPerCC);
			sinoImp.setProperties(props);
			//these properties are preserved in the images tiff file header
						
			//put the sinogram into the image
			ImageProcessor sinoIP = sinoImp.getProcessor();
			sinoIP.setPixels(sino);
			
			// Set the sinogram X,Y units
			//The sinogram pixel values are in per pixel units
			Calibration  imgCal = imp.getCalibration();		
			String unit = imgCal.getUnit();	// bark if not "cm" ?
			double pixSize = imgCal.getX(1); //cm per pixel
			Calibration sinoCal = sinoImp.getCalibration();
            sinoCal.setXUnit(unit);
            sinoCal.setYUnit("Deg");
            sinoCal.pixelWidth = pixSize;
            sinoCal.pixelHeight = 180.0/bppSet.numAng;
            
			sinoImp.show();
			
			if(scale16)
			{
				IJ.run(sinoImp, "Multiply...", "value=6000");
				ImageConverter.setDoScaling(false);
				IJ.run(sinoImp, "16-bit", "");
			}					
			IJ.run(sinoImp, "Enhance Contrast", "saturated=0.35");
			
		}
	}
	
	//*******************************************************************************

	@SuppressWarnings("unchecked")
	private void GetSelections(GenericDialog gd)
	{
		String str;
		
		bppSet.pixSizeCM = imp.getCalibration().pixelWidth;
		
		Vector<Choice> choices = gd.getChoices();
		bppSet.target = choices.get(0).getSelectedItem();
		bppSet.filter = choices.get(1).getSelectedItem();
		bppSet.filterGmPerCC = mmc.getAtomGmPerCC(bppSet.filter);
		
		Vector<TextField> txtFlds = gd.getStringFields();
		bppSet.detFormula = txtFlds.get(0).getText();
		
		Vector<TextField> numFlds = gd.getNumericFields();
		str = numFlds.get(0).getText();
		if(isNumeric(str)) 	bppSet.kv =  Double.valueOf(str);

		str = numFlds.get(1).getText();
		if(isNumeric(str)) 	bppSet.ma =  Double.valueOf(str);

		str = numFlds.get(2).getText();
		if(isNumeric(str)) 	bppSet.nBins =  Integer.valueOf(str);

		str = numFlds.get(3).getText();
		if(isNumeric(str)) 	bppSet.minKV =  Double.valueOf(str);

		str = numFlds.get(4).getText();
		if(isNumeric(str)) 	bppSet.filterCM =  Double.valueOf(str);

		str = numFlds.get(5).getText();
		if(isNumeric(str)) 	bppSet.numAng =  Integer.valueOf(str);

		str = numFlds.get(6).getText();
		if(isNumeric(str)) 	bppSet.detCM =  Float.valueOf(str);

		str = numFlds.get(7).getText();
		if(isNumeric(str)) 	bppSet.detGmPerCC =  Float.valueOf(str);
		
		Vector<Checkbox> ckBoxes = gd.getCheckboxes();
		scale16 = ckBoxes.get(0).getState();
		padImage = ckBoxes.get(1).getState();
		
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
//		for(int i=0;i< gd.getComponentCount();i++)
//		{
//			IJ.log("Component[" + i + "]=" + gd.getComponent(i).toString());			
//		}

		if(e!=null)
		{
			int index;
			Label label;
			GetSelections(gd);
			@SuppressWarnings("unchecked")
			Vector<TextField> numFldVec = gd.getNumericFields();
			
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
					
					int detPixCnt= imp.getWidth();
					index = GetLabelIndex(gd,"Detector Pixel");
					label = (Label) gd.getComponent(index);
					label.setText("Detector Pixel Count = " + detPixCnt);
						
					int numAngles = (int) (Math.ceil(Math.PI*detPixCnt/2));
					index = GetTextFieldIndex(numFldVec,"numAng");
					numFldVec.get(index).setText(String.valueOf(numAngles));
					break;
				case "scale16":
					break;
				}
				
			}			
		}
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

	//*********************************************************************************/

}
