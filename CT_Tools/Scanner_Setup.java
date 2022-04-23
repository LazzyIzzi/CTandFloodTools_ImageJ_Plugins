package CT_Tools;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Vector;

import ij.*;
import ij.plugin.*;
import ij.gui.*;
import ij.measure.ResultsTable;
import jhd.Serialize.Serializer;
import tagTools.common.MatlListTools2;
import jhd.MuMassCalculator.*;

public class Scanner_Setup implements PlugIn, DialogListener, ActionListener
//public class Beam_Hardening_Estimator implements PlugIn, ActionListener
{
	MuMassCalculator mmc= new MuMassCalculator();
	//A serializable class used to store the dialog settings between calls
	MuMassCalculator.BeamHardenParams bhSet = new MuMassCalculator.BeamHardenParams();
	//The class used to serialize (save) the users selections
	Serializer ser = new Serializer();
	//A class to manage materials lists organized as CSV tag files
	MatlListTools2 mlt=new MatlListTools2();
	//A tagSet consists of a String tagHdr[4] and an ArrayList of tag data
	MatlListTools2.TagSet tagSet;
	//Used to convert the tagData ArrayList to the separate arrays needed by GenericDialog
	String[] matlName;
	String[] matlFormula;
	double[] matlGmPerCC;
	
	//ImageJ windows
	Plot gSpectPlot,gTauPlot;
	PlotWindow gSpectPlotWindow,gTauPlotWindow;
	ResultsTable gSpectRt;
	GenericDialog gd;
	
	//A bunch of constants
	static final String mySettingsTitle = "Beam_Harden_Params";
	String dir = IJ.getDirectory("plugins");	
	String settingsPath = dir+ "DialogSettings" + File.separator + mySettingsTitle + ".ser";
	String[] atomSymb = mmc.getAtomSymbols();
	String[] filterSymb = {"Ag","Al","Cu","Er","Fe","Mo","Nb","Ni","Rh","Ta","Zr"};
	String[] targetSymb = {"Ag","Au","Cr","Cu","Mo","Rh","W"};	
	String gSpectPlotTitle = "X-ray Spectra";
	final String myDialogTitle = "Beam Hardening Workbench";
	final String myResultsTitle = "BH Workbench Results";
	final int pWidth=600,pHeight=300;//275;
	final Color buff = new Color(250,240,200);

	// A factor to estimate the thickness of the thinnest part of the sample
	// Since most detectors are ~1000 pixels .001 is a about a one pixel path
	// paths less than 1 pixel will be dominated by partial voxel effects
	static final double gThin = .001;
		
	//***********************************************************************
	/**Loads the materials list, builds the materials arrays, builds the BHparams list*/
	@Override
	public void run(String arg)
	{
		
		if(IJ.versionLessThan("1.53k")) return;
		
		//Location of the default materials list
		String dir = IJ.getDirectory("plugins");
		String defaultFilePath = dir + "DialogData\\DefaultMaterials.csv";
		
		tagSet = mlt.loadTagFile(defaultFilePath);
		//Get names array from TagSet
		matlName = new String[tagSet.tagData.size()];
		matlFormula = new String[tagSet.tagData.size()];
		matlGmPerCC = new double[tagSet.tagData.size()];
		int i=0;
		for(MatlListTools2.TagData td : tagSet.tagData)
		{
			matlName[i]= td.matlName;
			matlFormula[i] = td.matlFormula;
			matlGmPerCC[i] = td.matlGmPerCC;
			i++;
		}
		//Read the saved dialog settings
		bhSet = (MuMassCalculator.BeamHardenParams)ser.ReadSerializedObject(settingsPath);
		if(bhSet==null)
		{
			InitializeSettings();
		}

		//LoadMaterialsFile(defaultFilePath);
			
		DoDialog();
	}
	
	//*********************************************************************************/

	public void DoDialog()
	{
//		String dir = IJ.getDirectory("plugins");
//		dir= dir.replace("\\","/");
//		String myURL = "file:///C:/Users/John/git/LazzyIzzi.github.io/BHestimator.html";
		String[] plotChoices = {"KeV","Angstroms"};


		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);

		gd = GUI.newNonBlockingDialog(myDialogTitle);

		gd.addDialogListener(this);

	
		//X-ray Source
		gd.setInsets(10,0,0);
		gd.addMessage("X-ray Source________________",myFont,Color.BLACK);
		gd.addChoice("Target",targetSymb,bhSet.target);
		gd.addNumericField("KV", bhSet.kv);
		gd.addNumericField("mA", bhSet.ma);

		//Filter
		gd.setInsets(10,0,0);
		gd.addMessage("Source Filter________________",myFont,Color.BLACK);
		gd.addChoice("Material",filterSymb,bhSet.filter);
		gd.addNumericField("Thickness(cm)", bhSet.filterCM);

		//Sample
		gd.setInsets(10,0,0);
		gd.addMessage("Sample____________________",myFont,Color.BLACK);
		gd.addChoice("Formulas:", matlName, matlName[0]);
		gd.addStringField("Formula", bhSet.matlFormula);
		gd.addNumericField("Thickness(cm)", bhSet.matlCM);
		gd.addNumericField("Density(gm/cc)", bhSet.matlGmPerCC);

		//Detector
		gd.setInsets(10,0,0);
		gd.addMessage("Detector___________________",myFont,Color.BLACK);
		gd.addChoice("Formulas:", matlName, matlName[0]);
		gd.addStringField("Formula", bhSet.detFormula);
		gd.addNumericField("Thickness(cm)", bhSet.detCM);
		gd.addNumericField("Density(gm/cc)", bhSet.detGmPerCC);
		
		//Plot Range
		gd.setInsets(10,0,0);
		gd.addMessage("Plot Range_______________",myFont,Color.BLACK);
		gd.addNumericField("Minimum (KeV)", bhSet.kvMin);
		gd.addNumericField("Inc (KeV)", bhSet.kvInc);
		gd.setInsets(0,100,0);
		gd.addRadioButtonGroup(null, plotChoices, 2, 1, bhSet.plotChoice);

		gd.setInsets(20,80,10);
		gd.addButton("Update Plot", this);
//		gd.setInsets(0,80,10);
//		gd.addButton("Load Materials File", this);
		gd.setAlwaysOnTop(false);

		gd.addHelp("https://lazzyizzi.github.io/CtScannerSetup.html");
		
		@SuppressWarnings("unchecked")		
		Vector<Choice> chv = gd.getChoices();
		chv.get(2).setName("sampleNames");
		chv.get(3).setName("detectorNames");
//		for( Choice ch : chv)
//		{
//			IJ.log(ch.toString());
//		}

		@SuppressWarnings("unchecked")
		Vector<TextField> tfv = gd.getStringFields();
		tfv.get(0).setName("sampleFormula");
		tfv.get(1).setName("detectorFormula");
//		IJ.log("*******************************");
//		for(TextField tf : tfv)
//		{
//			IJ.log(tf.toString());
//		}
		
		@SuppressWarnings("unchecked")		
		Vector<TextField> ntfv = gd.getNumericFields();
		
//		IJ.log("Before*************************");
//		for(TextField ntf : ntfv)
//		{
//			IJ.log(ntf.toString());
//		}
		ntfv.get(4).setName("sampleDensity");
		ntfv.get(6).setName("detectorDensity");
//		IJ.log("After*************************");
//		for(TextField ntf : ntfv)
//		{
//			IJ.log(ntf.toString());
//		}
		
		
		gd.showDialog();
				
		//Getting dialog settings and displaying results are event driven.
		//See actionPerformed()		
		if (gd.wasCanceled())
		{
			if(gSpectPlotWindow!=null) gSpectPlotWindow.close();
			if(gTauPlotWindow!=null) gTauPlotWindow.close();
			if(gSpectRt!=null)
			{
				IJ.selectWindow(myResultsTitle);
				IJ.run("Close");
			}
			return;
		}
		else if(gd.wasOKed())
		{
			GetSelections(gd);
			ser.SaveObjectAsSerialized(bhSet, settingsPath);
			if(gSpectPlotWindow!=null) gSpectPlotWindow.close();
			if(gTauPlotWindow!=null) gTauPlotWindow.close();
			if(gSpectRt!=null)
			{
				IJ.selectWindow(myResultsTitle);
				IJ.run("Close");
			}
			return;
		}	
	}

	//***************************************************************************************
	
	/**Displays the intensity vs energy distribution at strategic locations in radiography
	 * system consisting of a conventional source, a filter, a sample and a detector.
	 * Echos the input data and outputs key performance metrics.
	 * 
	 */
	private void ShowSpectResults()
	{
		
		int i=0;
		int size = (int)((bhSet.kv-bhSet.kvMin)/bhSet.kvInc) +1;
		//The source
		double[] src = new double[size];
		double[] kevList = new double[size];
		
		//The Scanner absorbances
		double[] filterTau = new double[size];
		double[] sampleTau = new double[size];
		double[] thinSampleTau = new double[size];
		double[] detTau = new double[size];
				
		//The Spectra, Some currently unnecessary spectra commented out
		//double[] srcNoFiltDet = new double[size];
		double[] srcFilt = new double[size];
		double[] srcFiltDet = new double[size];
		//double[] srcNoFiltSamp = new double[size];
		double[] srcFiltSamp = new double[size];
		double[] srcFiltThinSamp = new double[size];
		double[] srcFiltSampDet = new double[size];
		double[] srcFiltThinSampDet = new double[size];

		//Integration
		double srcIntg=0;
		//double srcNoFiltDetIntg=0;
		double srcFiltIntg=0;
		double srcFiltDetIntg=0;
		//double srcNoFiltSampIntg=0;
		//double srcFiltSampIntg=0;
		//double srcFiltThinSampIntg=0;
		double srcFiltSampDetIntg=0;
		double srcFiltThinSampDetIntg=0;
	    
		double meV;
		i=0;
		//for(double keV = bhSet.kvMin; keV <= bhSet.kv; keV+= bhSet.kvInc)
		for(double keV = bhSet.kv; keV >= bhSet.kvMin; keV-= bhSet.kvInc)
		{
			//The Source Spectrum
			meV = keV / 1000;
			kevList[i] = keV;			
			src[i] = mmc.spectrumKramers(bhSet.kv, bhSet.ma, bhSet.target, meV);//get the source continuum intensity spectrum			

            //The component attenuations
            filterTau[i] = mmc.getMuMass(bhSet.filter, meV, "TotAttn") * bhSet.filterCM * bhSet.filterGmPerCC;           
            detTau[i] = mmc.getMuMass(bhSet.detFormula, meV, "TotAttn") * bhSet.detCM * bhSet.detGmPerCC;
            sampleTau[i] = mmc.getMuMass(bhSet.matlFormula, meV, "TotAttn") * bhSet.matlCM * bhSet.matlGmPerCC;
            thinSampleTau[i] = mmc.getMuMass(bhSet.matlFormula, meV, "TotAttn") * gThin * bhSet.matlCM * bhSet.matlGmPerCC; // 0.001 = approximately 1 pixel
            
            //The intensity spectra
            //srcNoFiltDet[i] = src[i] * (1 - Math.exp(-detTau[i]))    //The unfiltered source Currently unused
            srcFilt[i] = src[i] * Math.exp(-filterTau[i]);            //The filtered source
            srcFiltDet[i] = srcFilt[i] * (1 - Math.exp(-detTau[i]));  //The filtered source detected
            //srcNoFiltSamp[i] = src[i] * Math.exp(-sampleTau[i])      //The source attenuated by only the sample Currently unused
            srcFiltSamp[i] = srcFilt[i] * Math.exp(-sampleTau[i]);    //The source attenuated by the bhSet.filter and the sample
            srcFiltThinSamp[i] = srcFilt[i] * Math.exp(-thinSampleTau[i]);    //The source attenuated by the bhSet.filter and a arbitrary very thin part of the sample
            srcFiltSampDet[i] = srcFiltSamp[i] * (1 - Math.exp(-detTau[i]));  //The source attenuated by the bhSet.filter and the sample detected
            srcFiltThinSampDet[i] = srcFiltThinSamp[i] * (1 - Math.exp(-detTau[i])); //The source attenuated by the bhSet.filter and the thin sample detected

            //Integrate the spectra
            //srcNoFiltDetIntg += srcNoFiltDet(i) *  bhSet.kvInc; //Currently unused
            srcIntg += src[i]*bhSet.kvInc;
            srcFiltIntg += srcFilt[i] * bhSet.kvInc;
            srcFiltDetIntg += srcFiltDet[i] * bhSet.kvInc;
            //srcNoFiltSampIntg +=srcNoFiltSamp[i] *  bhSet.kvInc;//Currently unused
            //srcFiltSampIntg += srcFiltSamp[i] * bhSet.kvInc;//Currently unused
            //srcFiltThinSampIntg += srcFiltThinSamp[i] * bhSet.kvInc;//Currently unused
            srcFiltSampDetIntg += srcFiltSampDet[i] * bhSet.kvInc;
            srcFiltThinSampDetIntg += srcFiltThinSampDet[i] * bhSet.kvInc;
            
 			i++;
		}
		
//		IJ.log("srcIntg="+srcIntg);
//		IJ.log("srcFiltIntg="+srcFiltIntg);
//		IJ.log("srcFiltDetIntg="+srcFiltDetIntg);
//		IJ.log("srcFiltSampDetIntg="+srcFiltSampDetIntg);
//		IJ.log("srcFiltThinSampDetIntg="+srcFiltThinSampDetIntg);
		
		//compute the effective energy for the filtered source spectrum
		//The effective energy is the equivalent monochromatic x-ray energy that will produce
		//the same attenuation by a sample measured with a broad-spectrum source.

        double sampleTauDet = -Math.log(srcFiltSampDetIntg / srcFiltDetIntg);
        double sampleMuLin = sampleTauDet / bhSet.matlCM;
        double[] sampleEff = mmc.getMeVfromMuLin(bhSet.matlFormula, sampleMuLin, bhSet.matlGmPerCC,"TotAttn");
 
        double thinSampleTauDet = -Math.log(srcFiltThinSampDetIntg / srcFiltDetIntg);
        double thinSampleMuLin = thinSampleTauDet / (gThin * bhSet.matlCM);
        double[] thinSampleEff = mmc.getMeVfromMuLin(bhSet.matlFormula, thinSampleMuLin, bhSet.matlGmPerCC,"TotAttn");
              
        //convert from energy to wavelength if requested
		String xAxisTitle = "KeV";
		if(bhSet.plotChoice == "Angstroms")
		{
			for(i=0;i<kevList.length;i++)
			{
				kevList[i] = 12.41/kevList[i];
			}
			xAxisTitle = "Wavelength(Angstroms)";
		}

		//Find min and max MuMass for the source spectrum
		double countsMax = src[0];
		double countsMin = src[0];
		for(i=1;i<src.length;i++)
		{
			if(countsMax < src[i]) countsMax = src[i];
			if(countsMin > src[i]) countsMin = src[i];
		}
		
		//Plot the results
		Plot newPlot = null;
		String legend = "Source Counts\nFiltered\nSample Trans\nFiltered Detected (Io)\nSample Detected (I)";

		//If the user closes the plot window but the gPlotWindow is not set to null
		if(gSpectPlotWindow!=null && gSpectPlotWindow.isClosed())
		{
			this.gSpectPlotWindow.dispose();//probably already called by the close() method
			this.gSpectPlotWindow=null;
			//System.gc(); //This may be overkill
		}

		if(gSpectPlotWindow==null)
		{
			gSpectPlot = new Plot(gSpectPlotTitle,xAxisTitle,"Counts");	
			gSpectPlot.setSize(pWidth, pHeight);			
			gSpectPlot.setBackgroundColor(buff);
			gSpectPlot.setLogScaleY();
			gSpectPlot.setFontSize(14);

			//Source Intensity
			gSpectPlot.setLineWidth(2);
			gSpectPlot.setColor(Color.blue);
			gSpectPlot.addPoints(kevList,src,Plot.LINE);

			// intensity after bhSet.filter
			gSpectPlot.setColor(Color.red);
			gSpectPlot.addPoints(kevList,srcFilt,Plot.LINE);

			// intensity after sample
			gSpectPlot.setColor(Color.green);
			gSpectPlot.addPoints(kevList,srcFiltSamp,Plot.LINE);
			//plot.addPoints(kevList,srcFiltDet,Plot.LINE);

			// detected intensity after bhSet.filter i.e. Io
			gSpectPlot.setColor(Color.gray);
			gSpectPlot.addPoints(kevList,srcFiltDet,Plot.LINE);

			// detected intensity
			gSpectPlot.setColor(Color.BLACK);
			gSpectPlot.addPoints(kevList,srcFiltSampDet,Plot.LINE);
			gSpectPlot.setLimits(kevList[0], kevList[kevList.length-1], countsMin, countsMax);

			gSpectPlot.addLegend(legend);
			gSpectPlotWindow = gSpectPlot.show();
			gSpectPlotWindow.setLocation(gd.getLocation().x+gd.getSize().width,gd.getLocation().y + gTauPlotWindow.getSize().height);
		}
		else 
		{
			//The user may have changed the plot limits prior to clicking the "Run Now" Checkbox
			//get the existing plot limits from the plot window
			//Plot oldPlot = 	gPlotWindow.getPlot();
			//double[] oldPlotLimits = oldPlot.getLimits();
			double[] oldPlotLimits = gSpectPlotWindow.getPlot().getLimits();

			newPlot = new Plot(gSpectPlotTitle,xAxisTitle,"Counts");	
			newPlot.setSize(pWidth, pHeight);			
			newPlot.setBackgroundColor(buff);
			
			//Source Intensity
			newPlot.setLineWidth(2);
			newPlot.setColor(Color.blue);
			newPlot.addPoints(kevList,src,Plot.LINE);

			// intensity after bhSet.filter
			newPlot.setColor(Color.red);
			newPlot.addPoints(kevList,srcFilt,Plot.LINE);

			// intensity after sample
			newPlot.setColor(Color.green);
			newPlot.addPoints(kevList,srcFiltSamp,Plot.LINE);

			// detected intensity after bhSet.filter i.e. Io
			newPlot.setColor(Color.gray);
			newPlot.addPoints(kevList,srcFiltDet,Plot.LINE);

			// detected intensity
			newPlot.setColor(Color.BLACK);
			newPlot.addPoints(kevList,srcFiltSampDet,Plot.LINE);
			
			newPlot.addLegend(legend);
			
			// update the keV limits for keV or Angstroms, keep the counts limits 
			newPlot.setLimits(kevList[0], kevList[kevList.length-1], oldPlotLimits[2], oldPlotLimits[3]);
			gSpectPlotWindow.drawPlot(newPlot);
			gSpectPlotWindow.setLocation(gd.getLocation().x+gd.getSize().width,gd.getLocation().y + gTauPlotWindow.getSize().height);
		}
		

		//Show the results
		gSpectRt = ResultsTable.getResultsTable(myResultsTitle);
		if(gSpectRt==null)
		{
			gSpectRt=new ResultsTable();
			gSpectRt.setPrecision(3);
		}
		gSpectRt.incrementCounter();
		gSpectRt.addValue("Sample", bhSet.matlFormula);
		gSpectRt.addValue("S CM", bhSet.matlCM);
		gSpectRt.addValue("S gm/cc", bhSet.matlGmPerCC);
		gSpectRt.addValue("S Tau", sampleTauDet);
		if(sampleEff!=null && thinSampleEff!=null)
		{
			for(int j=0;j<sampleEff.length;j++)
			{					
				gSpectRt.addValue("Eeff (keV) " + j, sampleEff[j]*1000);
				//gSpectRt.addValue("Min Eeff (keV) " + j, thinSampleEff[j]*1000);
				double drift = sampleEff[j] -thinSampleEff[j];
				//gSpectRt.addValue("Drift (keV) " + j, drift*1000);
				gSpectRt.addValue("BH %" + j, drift/sampleEff[j]*100);
			}
		}
		else
		{
			gSpectRt.addValue("Sample Eeff0", "NoSoln");
		}
		double detectorAbs = srcFiltDetIntg / srcFiltIntg;
		double filterTrans = srcFiltIntg / srcIntg;
		gSpectRt.addValue("Photon Use%",detectorAbs*filterTrans*100);

		gSpectRt.addValue("Source kv", bhSet.kv);
		gSpectRt.addValue("Src ma", bhSet.ma);
		gSpectRt.addValue("Src Anode", bhSet.target);
		
		gSpectRt.addValue("Filter", bhSet.filter);
		gSpectRt.addValue("F CM", bhSet.filterCM);
		gSpectRt.addValue("F gm/cc", bhSet.filterGmPerCC);
		gSpectRt.addValue("F Trans", filterTrans);
					
		gSpectRt.addValue("Detector", bhSet.detFormula);
		gSpectRt.addValue("Det CM", bhSet.detCM);
		gSpectRt.addValue("Det gm/cc", bhSet.detGmPerCC);
		gSpectRt.addValue("Det Absorp.", detectorAbs);
		
		gSpectRt.show(myResultsTitle);
		Window win = WindowManager.getWindow(myResultsTitle);
		win.setLocation(gd.getLocation().x,gd.getLocation().y + gd.getSize().height);
		Dimension d=win.getSize();
		if(d.width < gd.getSize().width + gTauPlotWindow.getSize().width)
		{
			d.width = gd.getSize().width + gTauPlotWindow.getSize().width;
		}
		win.setSize(d);
	

	}

	//***************************************************************************************
	
	private void ShowTauPlot()
	{
		//This method shows the tau vs thickness plot
		final double pathStep = 0.1;
		int size = (int)Math.ceil((bhSet.matlCM)/pathStep) + 1;
		
		//The source
		double src;
		
		//The Scanner absorbances
		double filterTau;
		double sampleTau;
		double detTau;
				
		//The Spectra
		double srcFilt;
		double srcFiltDet;
		double srcFiltSamp;
		double srcFiltSampDet;

		//Integration
		double srcFiltDetIntg;
		double srcFiltSampDetIntg;
		
		//Results
		double[] sampleTauDet = new double[size];
		double[] pathList = new double[size];
	    
		double keV,meV;
		double path=0;
		int i;
		for( i = 0; i < size; i++)
		{
			srcFiltDetIntg = 0;
			srcFiltSampDetIntg =0;
			for(keV = bhSet.kv; keV >= bhSet.kvMin; keV-= bhSet.kvInc)
			{
				//The Source Intensity at meV
				meV = keV / 1000;
				src = mmc.spectrumKramers(bhSet.kv, bhSet.ma, bhSet.target, meV);	

				//The component attenuations
				filterTau = mmc.getMuMass(bhSet.filter, meV, "TotAttn") * bhSet.filterCM * bhSet.filterGmPerCC;           
				detTau = mmc.getMuMass(bhSet.detFormula, meV, "TotAttn") * bhSet.detCM * bhSet.detGmPerCC;
				sampleTau = mmc.getMuMass(bhSet.matlFormula, meV, "TotAttn") * path * bhSet.matlGmPerCC;

				//The intensities
				srcFilt = src * Math.exp(-filterTau);            //The filtered source
				srcFiltDet = srcFilt * (1 - Math.exp(-detTau));  //The filtered source detected
				srcFiltSamp = srcFilt * Math.exp(-sampleTau);    //The source attenuated by the bhSet.filter and the sample
				srcFiltSampDet = srcFiltSamp * (1 - Math.exp(-detTau));  //The source attenuated by the bhSet.filter and the sample detected

				//Integrate
				srcFiltDetIntg += srcFiltDet;
				srcFiltSampDetIntg += srcFiltSampDet;
			}
			sampleTauDet[i] = -Math.log(srcFiltSampDetIntg / srcFiltDetIntg);
			pathList[i] = path;
			path += pathStep;
		}
		
             
		//Find min and max MuMass for the source spectrum
		double countsMax = sampleTauDet[0];
		double countsMin = sampleTauDet[0];
		for(i=1;i<sampleTauDet.length;i++)
		{
			if(countsMax < sampleTauDet[i]) countsMax = sampleTauDet[i];
			if(countsMin > sampleTauDet[i]) countsMin = sampleTauDet[i];
		}
		
		//Plot the results
		String title =  "Attenuation vs Thickness";
		Plot newPlot = null;
		String legend = bhSet.matlFormula + ", " + bhSet.kv +"KV" + ", " + bhSet.ma + "mA" + ", " + bhSet.filterCM + "cm "+ bhSet.filter;
		
		//If the user closes the plot window but the gPlotWindow is not set to null
		if(gTauPlotWindow!=null && gTauPlotWindow.isClosed())
		{
			this.gTauPlotWindow.dispose();//probably already called by the close() method
			this.gTauPlotWindow=null;
			//System.gc(); //This may be overkill
		}

		if(gTauPlotWindow==null )
		{
			gTauPlot = new Plot(title,"Path(cm)","Attenuation");
			gTauPlot.setSize(pWidth, pHeight);			
			gTauPlot.setBackgroundColor(buff);
			gTauPlot.setFontSize(14);
			gTauPlot.setLineWidth(2);
			gTauPlot.setColor(Color.blue);
			gTauPlot.addPoints(pathList,sampleTauDet,Plot.LINE);
			gTauPlotWindow = gTauPlot.show();
			gTauPlot.addLegend(legend);			
			gTauPlotWindow.setLocation(gd.getLocation().x+gd.getSize().width,gd.getLocation().y);
		}
		else 
		{
			newPlot = new Plot(title,"Path(cm)","Attenuation");
			newPlot.setSize(pWidth, pHeight);			
			newPlot.setBackgroundColor(buff);
			newPlot.setLineWidth(2);
			newPlot.setColor(Color.blue);
			newPlot.addPoints(pathList,sampleTauDet,Plot.LINE);
			newPlot.addLegend(legend);
			gTauPlotWindow.drawPlot(newPlot);
			gTauPlotWindow.setLocation(gd.getLocation().x+gd.getSize().width,gd.getLocation().y);
		}
	}

	//***************************************************************************************

	private void InitializeSettings()
	{
		bhSet = new MuMassCalculator.BeamHardenParams();
		//X-ray Source
		bhSet.target = "W";
		bhSet.kv = 160;
		bhSet.ma = 100;

		//Filter
		bhSet.filter = "Cu";
		bhSet.filterCM = 0.01;
		bhSet.filterGmPerCC = mmc.getAtomGmPerCC(bhSet.filter);

		//Sample
		bhSet.matlFormula = tagSet.tagData.get(0).matlFormula;//"Ca:1:C:1:O:3";
		bhSet.matlCM = 3;
		bhSet.matlGmPerCC = tagSet.tagData.get(0).matlGmPerCC;//2.71;

		//Detector
		bhSet.detFormula = "Cs:1:I:1";
		bhSet.detCM = 0.01;;
		bhSet.detGmPerCC = 4.51;
		
		//Plot Range
		bhSet.kvMin = 10;
		bhSet.kvInc = 1;
		bhSet.plotChoice = "KeV";
	}
	
	//***************************************************************************************
	
	@SuppressWarnings("unchecked")
	private void GetSelections(GenericDialog gd)
	{
		String str;
		//The pull-down materials menus are not parameter choices.
		//They are used to put the parameters into the text fields
		Vector<Choice> choices= gd.getChoices();
		bhSet.target = choices.get(0).getSelectedItem();
		bhSet.filter =choices.get(1).getSelectedItem();
		bhSet.filterGmPerCC = mmc.getAtomGmPerCC(bhSet.filter);
		
		Vector<TextField> numberFields = gd.getNumericFields();
		
		str = numberFields.get(0).getText();
		if(isNumeric(str)) bhSet.kv = Double.parseDouble(str); 

		str = numberFields.get(1).getText();
		if(isNumeric(str)) bhSet.ma = Double.parseDouble(str); 

		str = numberFields.get(2).getText();
		if(isNumeric(str)) bhSet.filterCM = Double.parseDouble(str); 

		str = numberFields.get(3).getText();
		if(isNumeric(str)) bhSet.matlCM = Double.parseDouble(str); 

		str = numberFields.get(4).getText();
		if(isNumeric(str)) bhSet.matlGmPerCC = Double.parseDouble(str); 

		str = numberFields.get(5).getText();
		if(isNumeric(str)) bhSet.detCM = Double.parseDouble(str); 

		str = numberFields.get(6).getText();
		if(isNumeric(str)) bhSet.detGmPerCC = Double.parseDouble(str); 

		str = numberFields.get(7).getText();
		if(isNumeric(str)) bhSet.kvMin = Double.parseDouble(str); 
		
		str = numberFields.get(8).getText();
		if(isNumeric(str)) bhSet.kvInc = Double.parseDouble(str); 		
		
		Vector<TextField> textFields = gd.getStringFields();
		bhSet.matlFormula = textFields.get(0).getText();
		bhSet.detFormula = textFields.get(1).getText();

		Vector<CheckboxGroup> ckBoxes = gd.getRadioButtonGroups();
		Checkbox ckBox = ckBoxes.get(0).getSelectedCheckbox();
		bhSet.plotChoice = ckBox.getLabel();				
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
	
	//***************************************************************************************
	
	public void actionPerformed(ActionEvent e)
	{
		if(e!=null)
		{
			switch(e.getActionCommand())
			{
			case "Update Plot":
				Window gdWin = WindowManager.getWindow(myDialogTitle);
				//gdWin.setBackground(buff);
				gdWin.setAlwaysOnTop(false);
				GetSelections(gd);
				ShowTauPlot();
				ShowSpectResults();
				break;
			}
		}
	}
	
	//***************************************************************************************

	@SuppressWarnings("unchecked")		
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{
		if(e!=null)
		{
			if(e.getSource() instanceof Choice)
			{
				int chi,tfi,ntfi; //the component indices
				Choice choiceSrc = (Choice)e.getSource();
				Vector<Choice> chv = gd.getChoices();
				Vector<TextField> tfv = gd.getStringFields();
				Vector<TextField> ntfv = gd.getNumericFields();

				switch(choiceSrc.getName())
				{
				case "sampleNames":
					chi  = chv.get(2).getSelectedIndex();
					tfi = GetTextFieldIndex(tfv,"sampleFormula");
					ntfi = GetTextFieldIndex(ntfv,"sampleDensity");
					if(tfi>=0)tfv.get(tfi).setText(matlFormula[chi]);
					if(ntfi>=0)ntfv.get(ntfi).setText(Double.toString(matlGmPerCC[chi]));
					break;
				case "detectorNames":
					chi  = chv.get(3).getSelectedIndex();
					tfi = GetTextFieldIndex(tfv,"detectorFormula");
					ntfi = GetTextFieldIndex(ntfv,"detectorDensity");
					if(tfi>=0)tfv.get(tfi).setText(matlFormula[chi]);
					if(ntfi>=0)ntfv.get(ntfi).setText(Double.toString(matlGmPerCC[chi]));
					break;
				}
			}
			GetSelections(gd);
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
}
