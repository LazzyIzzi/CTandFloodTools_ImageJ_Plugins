package CT_Tools;

/*
 * This plugin simulates a Fan beam CT scan from 0 to 360 degrees
 * of a segmented 2D image using a conventional x-ray source and a scintillation
 * detector.
 * 
 *  The input image must be segmented into N components. The pixel values of each component must be
 *  set to an integer (tagged) beginning 1, corresponding to a composition and density in a
 *  Materials text file.
 *
 * A Materials file is a simple text file containing
 * a formula in the Atom1:Count1:atom2:Count2 format ,comma, followed by a density in gmPerCC.
 * One formula,density pair per line.
 * The first material is assigned to tag=1
 * The second in assigned to tag=2 etc.
 * e.g. for an image of Sandstone with Calcite cements
 * Ca:1:C:1:O:3, 2.71
 * Si:1:O:2, 2.53
 * 
 * This may seem a bit awkward but it allows the user to create a libraries of tagged components
 * so they won't need to be typed in every time.  Excel csv files will work for this purpose.
 * 
 * The tags are used to convert the image to linear attenuation at each energy in the
 * polychromatic scan.
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
 * A CT scan is simulated by rotating the source detector about the center of the image and p
 * collecting projections to form a sinogram.
 * 
 * This version defines the scanner geometry in centimeters.
 * Attempts to serialize the dialog parameters.
 */

import ij.IJ;

import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.gui.*;
import ij.measure.Calibration;

import java.io.File;
import java.awt.*;
import java.util.ArrayList;
import java.util.Vector;

import jhd.MuMassCalculator.*;
import gray.AtomData.*;
import jhd.Projection.*;
import jhd.Serialize.*;
import tagTools.common.*;


//*******************************************************************************

public class TagSlice_To_Fan_Brems_Sinogram implements PlugInFilter , DialogListener//, ActionListener
{
	//Used to test formulas prior to launching the simulator
	MuMassCalculator mmc = new MuMassCalculator();
	//The class that does the simulation
	FanProjectors fanPrj = new FanProjectors();
	//The nested class containing the simulator's user supplied parameters
	FanProjectors.BremFanParams bfpSet =  new FanProjectors.BremFanParams();
	//The class used to serialize (save) the users selections
	Serializer ser = new Serializer();
	//The class used to manage materials Lists
	MatlListTools2 mlt=new MatlListTools2();	
	//The nested class containing  materials list tag information
	MatlListTools2.TagSet tagSet;
	//Arrays to unpack Text file materials lists
	String[] matlArr;
	String[] formula;
	double[] gmPerCC;
	//Checkboxes
	boolean scale16,padImage;
	
	GenericDialog gd = new GenericDialog(myDialogTitle);
	ImagePlus imp;
	
	//GLOBALS
	String[] targetSymb = {"Ag","Au","Cr","Cu","Mo","Rh","W"};
	String[] filterSymb = {"Ag","Al","Cu","Er","Mo","Nb","Rh","Ta"};
	
	static final String myDialogTitle = "Polychromatic Fan Beam CTscan";
	static final String mySettingsTitle = "Polychromatic_FanBeam_Params";
	
	//the full path to the dialog settings
	String dir = IJ.getDirectory("plugins");	
	String settingsPath = dir+ "DialogSettings" + File.separator + mySettingsTitle + ".ser";

	int oldImgWidth,oldImageHeight;
		
	//*******************************************************************************
	//The plugin setup
	@Override
	public int setup(String arg, ImagePlus imp)
	{
		this.imp = imp;
		return DOES_32;
	}

	//*******************************************************************************
	//The plugin run
	@Override
	public void run(ImageProcessor ip)
	{
		//the original image width and height
		oldImgWidth =ip.getWidth();
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

		String dir = IJ.getDirectory("plugins");
		String path = dir + "DialogData\\DefaultMaterials.csv";
		tagSet = mlt.loadTagFile(path);

		//Read the saved dialog settings
		bfpSet = (FanProjectors.BremFanParams)ser.ReadSerializedObject(settingsPath);
		
		if(bfpSet==null)
		{
			bfpSet = GetDialogDefaultSettings();
		}
				
		if(DoDialog())
		{
			if(ValidateParams(bfpSet))
			{
				DoRoutine(bfpSet);
				ser.SaveObjectAsSerialized(bfpSet, settingsPath);
			}
		}
	}
	
	//*******************************************************************************

	private FanProjectors.BremFanParams GetDialogDefaultSettings()
	{
		FanProjectors.BremFanParams dlogSet = new FanProjectors.BremFanParams();
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
		dlogSet.numAng=(int)(0.5*Math.PI*imp.getWidth()*1.5f);
		dlogSet.srcToDetCM=100;
		dlogSet.magnification=1.5f;

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
		//dir= dir.replace("\\","/");
		//String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";
		
		//srcToSampCM and detPixCnt are presented for user information
		bfpSet.pixSizeCM = imp.getCalibration().pixelWidth;
		double srcToSampCM = bfpSet.srcToDetCM/bfpSet.magnification;
		int detPixCnt= (int)(imp.getWidth()*bfpSet.magnification);

		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);

		gd.addDialogListener(this);
		gd.setInsets(10,0,0);
		gd.addMessage("This plugin scans tagged images\nto bremsstrahlung sinograms.",myFont,Color.BLACK);
		//X-ray Source
		gd.setInsets(10,0,0);
		gd.addMessage("X-ray Source________________",myFont,Color.BLACK);
		gd.addChoice("Target",targetSymb,"W");
		gd.addNumericField("KV", bfpSet.kv);
		gd.addNumericField("mA", bfpSet.ma);
		gd.addNumericField("KeV Bins", bfpSet.nBins);
		gd.addNumericField("Min KeV", bfpSet.minKV);
		
		//Filter
		gd.setInsets(10,0,0);
		gd.addMessage("Source Filter________________",myFont,Color.BLACK);
		gd.addChoice("Material",filterSymb,bfpSet.filter);
		gd.addNumericField("Thickness(cm)", bfpSet.filterCM);
		
		//CT
		gd.setInsets(10,0,0);
		gd.addMessage("360 degree Scan______________",myFont,Color.BLACK);
		gd.addNumericField("Suggested View Angles", bfpSet.numAng);
		gd.addNumericField("Source to Detector(cm):", bfpSet.srcToDetCM);
		gd.addNumericField("Magnification:", bfpSet.magnification);
		//Info
		gd.setInsets(10,0,0);
		gd.addMessage("Axis to Detector = " + String.format("%.3f" + " cm", bfpSet.srcToDetCM - srcToSampCM ));

		gd.setInsets(0,0,0);
		gd.addMessage("Detector Pixel Count = " + detPixCnt);

		//Detector
		gd.setInsets(10,0,0);
		gd.addMessage("Detector___________________",myFont,Color.BLACK);
		gd.addStringField("Formula", bfpSet.detFormula);
		gd.addNumericField("Thickness(cm)", bfpSet.detCM);
		gd.addNumericField("Density(gm/cc)", bfpSet.detGmPerCC);
		gd.addCheckbox("Scale to 16-bit proj", scale16);
		gd.addCheckbox("Pad Image", padImage);
		gd.addHelp("https://lazzyizzi.github.io/CTsimulator.html");
		//gd.setOKLabel("Not OK");
     	 		
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
		numFlds.get(6).setName("srcToDetCM");
		numFlds.get(7).setName("magnification");
		numFlds.get(8).setName("detCM");
		numFlds.get(9).setName("detGmPerCC");
		
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

	private boolean ValidateParams(FanProjectors.BremFanParams bfpSet)
	{
		//Test the formulas
		ArrayList<AtomData> formula;
		formula = mmc.createFormulaList(bfpSet.target);
		if(formula==null) {IJ.error(bfpSet.target + " Is not a valid target material"); return false;}
		formula = mmc.createFormulaList(bfpSet.filter);
		if(formula==null) {IJ.error(bfpSet.filter + " Is not a valid filter material"); return false;}
		
		if(bfpSet.matlFormula==null){IJ.error("Missing Formulas"); return false;}
		if(bfpSet.matlGmPerCC==null){IJ.error("Missing Densities"); return false;}
		
		for(int i=1;i< bfpSet.matlFormula.length;i++)
		{
			if(bfpSet.matlGmPerCC[i] < 0){IJ.error("Material 1 Density " + bfpSet.matlGmPerCC[i] + " Cannot be negative"); return false;}
			if(bfpSet.matlFormula[i] == null){IJ.error("Missing Formula at item " + i); return false;}
			formula = mmc.createFormulaList(bfpSet.matlFormula[i]);
			if(formula==null) {IJ.error(bfpSet.matlFormula[i] + " Is not a valid  material"); return false;}			
		}
		
		formula = mmc.createFormulaList(bfpSet.detFormula);
		if(formula==null) {IJ.error(bfpSet.detFormula + " Is not a valid detector material"); return false;}
		
		//Test the numbers
		if(bfpSet.kv < bfpSet.minKV){IJ.error("Source KV " + bfpSet.kv + " Must be greater than " + bfpSet.minKV + "KV"); return false;}
		if(bfpSet.kv <=0){IJ.error("Source KV " + bfpSet.kv + " Must be greater than 0 KV"); return false;}
		if(bfpSet.ma <=0){IJ.error("Source mA " + bfpSet.ma + " Must be greater than 0 mA"); return false;}
		if(bfpSet.nBins <=0){IJ.error("Bin Count " + bfpSet.nBins + " Must be greater than 0"); return false;}
		if(bfpSet.minKV > bfpSet.kv){IJ.error("Source minMV " + bfpSet.minKV + " Must be less than " + bfpSet.kv + "KV"); return false;}
		if(bfpSet.filterCM < 0){IJ.error("Filter Thickness " + bfpSet.filterCM + " Cannot be negative"); return false;}
		if(bfpSet.filterGmPerCC <= 0){IJ.error("Filter Density " + bfpSet.filterGmPerCC + " Cannot be negative"); return false;}
		
		if(bfpSet.numAng < 1){IJ.error("Number of angles " + bfpSet.numAng + " Cannot be negative or zero"); return false;}
		if(bfpSet.detCM <= 0){IJ.error("Detector Thickness " + bfpSet.detCM + " Cannot be negative"); return false;}
		if(bfpSet.detGmPerCC <= 0){IJ.error("Detector Densith " + bfpSet.detCM + " Cannot be negative or zero"); return false;}
		
		return true;
	}
	
	//*******************************************************************************

	private void DoRoutine(FanProjectors.BremFanParams bfpSet)
	{
		int width = imp.getWidth();
		int height = imp.getHeight();

		ImageProcessor ip = imp.getProcessor();
		Object image = ip.getPixels();

		if(image instanceof float[])
		{	
			//Run the Fan Beam scan
			//long startTime = System.nanoTime();
			float[] sino = fanPrj.imageToBremsstrahlungFanBeamSinogram2(bfpSet,(float [])image,width,height);
			//long endTime = System.nanoTime();
			//long duration = (endTime - startTime);
			//IJ.log("Execution time=" + duration + "nSec");
			
			// append "FanSino" to the image name
            String title;
			String name = imp.getTitle();
            int dotIndex = name.lastIndexOf(".");
            if(dotIndex != -1) title = name.substring(0, dotIndex);
            else title  = name;
            title += "_FanBremSino";
            //title += "_KV(" + bfpSet.kv +"-" + bfpSet.minKV + ")";
            //title += bfpSet.filter +"(" + bfpSet.filterCM + ")";
            //title += bfpSet.detFormula + "_" +String.format("%.2f" + "cm", bfpSet.detCM);
 
			ImagePlus sinoImp = IJ.createImage(title, (int)(imp.getWidth()*bfpSet.magnification), bfpSet.numAng,1,32);
			
			//Record the scan conditions in the image properties Info
			//They get posted in strange order
			//int propCnt = 22 + 4*bfpSet.matlGmPerCC.length;;		
			String[] props = new String[28];
			props[0]="Geometry"; 
			props[1]="Fan Beam"; 
			props[2]="Source";
			props[3]="Bremsstrahlung";
			props[4]="Source KV";
			props[5]=Double.toString(bfpSet.kv);
			props[6]="Source mA";
			props[7]=Double.toString(bfpSet.ma);
			props[8]="Source Target";
			props[9]=bfpSet.target;
			props[10]="Min keV";
			props[11]=Double.toString(bfpSet.minKV);
			props[12]="Bins";
			props[13]=Double.toString(bfpSet.nBins);
			props[14]="Filter";
			props[15]=bfpSet.filter;
			props[16]="Filter(cm)";
			props[17]=Double.toString(bfpSet.filterCM);			
			props[18]="Source To Detector";
			props[19]=Double.toString(bfpSet.srcToDetCM);			
			props[20]="Magnification";
			props[21]=Double.toString(bfpSet.magnification);			
			props[22]="Detector";
			props[23]=bfpSet.detFormula;
			props[24]="Detector(cm)";
			props[25]=Double.toString(bfpSet.detCM);
			props[26]="Detector(gm/cc)";
			props[27]=Double.toString(bfpSet.detGmPerCC);
			sinoImp.setProperties(props);
			//these properties are preserved in the files tiff header
			
			/*
			The Material tag list can be quite long
			Leave it to the user to keep track of which tags were used.
			for(int i=22, k=0;i<propCnt;i+=4,k++)
			{
				props[i]=("Matl " +(k+1));
				props[i+1]= bfpSet.matlFormula[k];
				props[i+2]= "Matl " + (k +1) + " gmPerCC";
				props[i+3]= Double.toString(bfpSet.matlGmPerCC[k]);
			}
			sinoImp.setProperties(props);
			*/
			
			//put the sinogram into the image
			ImageProcessor sinoIP = sinoImp.getProcessor();
			sinoIP.setPixels(sino);
			
			// Set the sinogram X,Y units
			//The pixel values are in per pixel units
			Calibration  imgCal = imp.getCalibration();		
			String unit = imgCal.getUnit();	// bark if not "cm" ?
			double pixSize = imgCal.getX(1); //cm per pixel
			Calibration sinoCal = sinoImp.getCalibration();
            sinoCal.setXUnit(unit);
            sinoCal.setYUnit("Deg");
            sinoCal.pixelWidth = pixSize;
            sinoCal.pixelHeight = 360.0/bfpSet.numAng;
            
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
	//DIALOG SUPPORT FUNCTIONS*******************************************************
	//*******************************************************************************

	@SuppressWarnings("unchecked")
	private void GetSelections(GenericDialog gd)
	{
		String str;

		bfpSet.pixSizeCM = imp.getCalibration().pixelWidth;
		
		Vector<Choice> choices = gd.getChoices();
		bfpSet.target = choices.get(0).getSelectedItem();
		bfpSet.filter = choices.get(1).getSelectedItem();
		bfpSet.filterGmPerCC = mmc.getAtomGmPerCC(bfpSet.filter);
		
		Vector<TextField> txtFlds = gd.getStringFields();
		bfpSet.detFormula = txtFlds.get(0).getText();
		
		Vector<TextField> numFlds = gd.getNumericFields();
		str = numFlds.get(0).getText();
		if(isNumeric(str)) 	bfpSet.kv =  Double.valueOf(str);

		str = numFlds.get(1).getText();
		if(isNumeric(str)) 	bfpSet.ma =  Double.valueOf(str);

		str = numFlds.get(2).getText();
		if(isNumeric(str)) 	bfpSet.nBins =  Integer.valueOf(str);

		str = numFlds.get(3).getText();
		if(isNumeric(str)) 	bfpSet.minKV =  Double.valueOf(str);

		str = numFlds.get(4).getText();
		if(isNumeric(str)) 	bfpSet.filterCM =  Double.valueOf(str);

		str = numFlds.get(5).getText();
		if(isNumeric(str)) 	bfpSet.numAng =  Integer.valueOf(str);

		str = numFlds.get(6).getText();
		if(isNumeric(str)) 	bfpSet.srcToDetCM =  Float.valueOf(str);

		str = numFlds.get(7).getText();
		if(isNumeric(str)) 	bfpSet.magnification =  Float.valueOf(str);

		str = numFlds.get(8).getText();
		if(isNumeric(str)) 	bfpSet.detCM =  Float.valueOf(str);

		str = numFlds.get(9).getText();
		if(isNumeric(str)) 	bfpSet.detGmPerCC =  Float.valueOf(str);
		
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
			//Vector<TextField> txtFldVec = gd.getStringFields();
			
			if(e.getSource() instanceof Checkbox)
			{
				Checkbox cb = (Checkbox) e.getSource();
				switch(cb.getName())
				{
				case "padImage":
					if(padImage)
					{
						int newWH = (int) (Math.ceil(Math.sqrt(oldImgWidth*oldImgWidth + oldImageHeight*oldImageHeight)));
						IJ.run("Canvas Size...", "width=" + newWH + " height="+newWH + " position=Center zero");
					}
					else
					{
						IJ.run("Canvas Size...", "width=" + oldImgWidth + " height="+oldImageHeight + " position=Center zero");				
					}
					
					double srcToSampCM = bfpSet.srcToDetCM/bfpSet.magnification;
					double sampToDetCM = bfpSet.srcToDetCM - srcToSampCM;
					int detPixCnt= (int)(imp.getWidth()*bfpSet.magnification);
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
					double srcToSampCM = bfpSet.srcToDetCM/bfpSet.magnification;
					double sampToDetCM = bfpSet.srcToDetCM - srcToSampCM;
					int detPixCnt= (int)(imp.getWidth()*bfpSet.magnification);
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
