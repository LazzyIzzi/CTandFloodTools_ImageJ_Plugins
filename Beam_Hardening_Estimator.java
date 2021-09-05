//package MuMassCalculator;

import java.awt.*;
import java.awt.event.*;
import ij.*;
import ij.plugin.*;
import ij.gui.*;
import ij.measure.ResultsTable;
import jhd.MuMassCalculator.*;

public class Beam_Hardening_Estimator implements PlugIn, DialogListener, ActionListener
//public class Beam_Hardening_Estimator implements PlugIn, ActionListener
{

	MuMassCalculator mmc= new MuMassCalculator();
	String[] atomSymb = mmc.getAtomSymbols();
	String[] filterSymb = {"Ag","Al","Cu","Er","Mo","Nb","Rh","Ta"};
	String[] targetSymb = {"Ag","Au","Cr","Cu","Mo","Rh","W"};

	Plot gPlot;
	PlotWindow gPlotWindow;
	ResultsTable gRt;
	static final String myDialogTitle = "Beam Hardening Estimator";
	static final String myResultsTitle = "Beam Hardening (BH) Results";
	
	// A factor to estimate the thickness of the thinnest part of the sample
	// Since most detectors are ~1000 pixels .001 is a about a one pixel path
	// paths less than 1 pixel will be dominated by partial voxel effects
	static final double gThin = .001;
	
	private class BHsettings
	{
		String plotChoice;

		//X-ray Source
		String target;
		double kv;
		double ma;

		//Filter
		String filter;
		double filterCM;
		double filterGmPerCC;

		//Sample
		String sampleFormula;
		double sampleCM;
		double sampleGmPerCC;

		//Energy Range
		double kvStart;
		double kvEnd;
		double kvInc;

		//Detector
		String detFormula;
		double detCM;
		double detGmPerCC;
		
		Boolean showResults;		
	}
	
	BHsettings bhSet = new BHsettings();
	
	//***********************************************************************

	@Override
	public void run(String arg)
	{
		
		if(IJ.versionLessThan("1.53k")) return;
		
		String dir = IJ.getDirectory("plugins");
		dir= dir.replace("\\","/");
		//String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";
		String[] plotChoices = {"KeV","Angstroms"};


		Font myFont = new Font(Font.DIALOG, Font.BOLD, 12);

		GenericDialog gd = GUI.newNonBlockingDialog(myDialogTitle);

		gd.addDialogListener(this);

		//General
		//setInsets(from top, from left, from bottom)
		gd.setInsets(0,0,0);
		gd.addMessage("Plots_______________________",myFont,Color.BLACK);
		gd.addRadioButtonGroup(null, plotChoices, 1, 2, plotChoices[1]);

		//X-ray Source
		gd.setInsets(10,0,0);
		gd.addMessage("X-ray Source________________",myFont,Color.BLACK);
		gd.addChoice("Target",targetSymb,"W");
		gd.addNumericField("KV", 160);
		gd.addNumericField("mA", 100);

		//Filter
		gd.setInsets(10,0,0);
		gd.addMessage("Source Filter________________",myFont,Color.BLACK);
		gd.addChoice("Material",filterSymb,"Cu");
		gd.addNumericField("Thickness(cm)", .4);

		//Sample
		gd.setInsets(10,0,0);
		gd.addMessage("Sample____________________",myFont,Color.BLACK);
		gd.addStringField("Formula", "Ca:1:C:1:O:3");
		gd.addNumericField("Thickness(cm)", 3);
		gd.addNumericField("Density(gm/cc)", 2.71);

		//Energy Range
		gd.setInsets(10,0,0);
		gd.addMessage("Energy Range_______________",myFont,Color.BLACK);
		gd.addNumericField("From (KeV)", 10);
		gd.addNumericField("To (KeV)", 160);
		gd.addNumericField("Inc (KeV)", 1);

		//Detector
		gd.setInsets(10,0,0);
		gd.addMessage("Detector___________________",myFont,Color.BLACK);
		gd.addStringField("Formula", "Cs:1:I:1");
		gd.addNumericField("Thickness(cm)", .01);
		gd.addNumericField("Density(gm/cc)", 4.51);
		
		gd.addCheckbox("Show Results", false);
		gd.setInsets(20, 80, 10);
		gd.addButton("Update Plot", this);
		gd.setAlwaysOnTop(true);

		//gd.addHelp(myURL);

		//Show the defaults
		GetSelections(gd);
		PlotSelections();

		gd.showDialog();

		if (gd.wasCanceled())
		{
			if(gPlotWindow!=null) gPlotWindow.close();
			if(gRt!=null)
			{
				IJ.selectWindow("Beam Hardening (BH) Results");
				IJ.run("Close");
			}
			return;
		}		
	}

	//*************************************************************************************
	private void PlotSelections()
	{
		
		if(bhSet.kvStart < bhSet.kvEnd)
		{
			double temp = bhSet.kvStart;
			bhSet.kvStart = bhSet.kvEnd;
			bhSet.kvEnd = temp;
		}

		int i=0;
		int size = (int)((bhSet.kvStart-bhSet.kvEnd)/bhSet.kvInc) +1;
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
		for(double keV = bhSet.kvStart; keV >= bhSet.kvEnd; keV-= bhSet.kvInc)
		{
			//The Source Spectrum
			meV = keV / 1000;
			kevList[i] = keV;			
			src[i] = mmc.spectrumKramers(bhSet.kv, bhSet.ma, bhSet.target, meV);//get the source continuum intensity spectrum			

            //The component attenuations
            filterTau[i] = mmc.getMuMass(bhSet.filter, meV, "TotAttn") * bhSet.filterCM * bhSet.filterGmPerCC;           
            detTau[i] = mmc.getMuMass(bhSet.detFormula, meV, "TotAttn") * bhSet.detCM * bhSet.detGmPerCC;
            sampleTau[i] = mmc.getMuMass(bhSet.sampleFormula, meV, "TotAttn") * bhSet.sampleCM * bhSet.sampleGmPerCC;
            thinSampleTau[i] = mmc.getMuMass(bhSet.sampleFormula, meV, "TotAttn") * gThin * bhSet.sampleCM * bhSet.sampleGmPerCC; // 0.001 = approximately 1 pixel
            
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
		
		//compute the effective energy for the filtered source spectrum
		//The effective energy is the equivalent monochromatic x-ray energy that will produce
		//the same attenuation by a sample measured with a broad-spectrum source.

        double sampleTauDet = -Math.log(srcFiltSampDetIntg / srcFiltDetIntg);
        double sampleMuLin = sampleTauDet / bhSet.sampleCM;
        double[] sampleEff = mmc.getMeVfromMuLin(bhSet.sampleFormula, sampleMuLin, bhSet.sampleGmPerCC,"TotAttn");
 
        double thinSampleTauDet = -Math.log(srcFiltThinSampDetIntg / srcFiltDetIntg);
        double thinSampleMuLin = thinSampleTauDet / (gThin * bhSet.sampleCM);
        double[] thinSampleEff = mmc.getMeVfromMuLin(bhSet.sampleFormula, thinSampleMuLin, bhSet.sampleGmPerCC,"TotAttn");
        
        if(bhSet.showResults)
        {
			gRt = ResultsTable.getResultsTable(myResultsTitle);
			if(gRt==null)
			{
				gRt=new ResultsTable();
				gRt.setPrecision(3);
			}
			gRt.incrementCounter();
			gRt.addValue("Sample", bhSet.sampleFormula);
			gRt.addValue("S CM", bhSet.sampleCM);
			gRt.addValue("S gm/cc", bhSet.sampleGmPerCC);
			gRt.addValue("S Tau", sampleTauDet);
			if(sampleEff!=null && thinSampleEff!=null)
			{
				for(int j=0;j<sampleEff.length;j++)
				{					
					gRt.addValue("Eeff (keV) " + j, sampleEff[j]*1000);
					gRt.addValue("Min Eeff (keV) " + j, thinSampleEff[j]*1000);
					double drift = sampleEff[j] -thinSampleEff[j];
					gRt.addValue("Drift (keV) " + j, drift*1000);
					gRt.addValue("BH %" + j, drift/sampleEff[j]*100);
				}
			}
			else
			{
				gRt.addValue("Sample Eeff0", "NoSoln");
			}

			gRt.addValue("Source kv", bhSet.kv);
			gRt.addValue("Src ma", bhSet.ma);
			gRt.addValue("Src Anode", bhSet.target);
			
			gRt.addValue("Filter", bhSet.filter);
			gRt.addValue("F CM", bhSet.filterCM);
			gRt.addValue("F gm/cc", bhSet.filterGmPerCC);
			double filterTrans = srcFiltIntg / srcIntg;
			gRt.addValue("F Trans", filterTrans);
						
			gRt.addValue("Detector", bhSet.detFormula);
			gRt.addValue("Det CM", bhSet.detCM);
			gRt.addValue("Det gm/cc", bhSet.detGmPerCC);
			double detectorAbs = srcFiltDetIntg / srcFiltIntg;
			gRt.addValue("Det Absorp.", detectorAbs);
			gRt.addValue("Photon Use%",detectorAbs*filterTrans*100);
			
			gRt.show("Beam Hardening (BH) Results");
         }
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
		String title = "Radiography Setup Intensities";
		Plot newPlot = null;
		if(gPlotWindow==null)
		{
			gPlot = new Plot(title,xAxisTitle,"Counts");	
			//plot.setSize(600, 650);
			
			gPlot.setLogScaleY();
			gPlot.setFontSize(14);

			//Source Intensity
			gPlot.setLineWidth(2);
			gPlot.setColor(Color.blue);
			gPlot.addPoints(kevList,src,Plot.LINE);

			// intensity after bhSet.filter
			gPlot.setColor(Color.red);
			gPlot.addPoints(kevList,srcFilt,Plot.LINE);

			// intensity after sample
			gPlot.setColor(Color.green);
			gPlot.addPoints(kevList,srcFiltSamp,Plot.LINE);
			//plot.addPoints(kevList,srcFiltDet,Plot.LINE);

			// detected intensity after bhSet.filter i.e. Io
			gPlot.setColor(Color.gray);
			gPlot.addPoints(kevList,srcFiltDet,Plot.LINE);

			// detected intensity
			gPlot.setColor(Color.BLACK);
			gPlot.addPoints(kevList,srcFiltSampDet,Plot.LINE);
			gPlot.setLimits(kevList[0], kevList[kevList.length-1], countsMin, countsMax);

			String legend = "Source Counts\nFiltered\nSample Trans\nFiltered Detected (Io)\nSample Detected (I)";
			gPlot.addLegend(legend);
			gPlotWindow = gPlot.show();
		}
		else 
		{
			//The user may have changed the plot limits prior to clicking the "Run Now" Checkbox
			//get the existing plot limits from the plot window
			//Plot oldPlot = 	gPlotWindow.getPlot();
			//double[] oldPlotLimits = oldPlot.getLimits();
			double[] oldPlotLimits = gPlotWindow.getPlot().getLimits();

			newPlot = new Plot(title,xAxisTitle,"Counts");	
			
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
			
			String legend = "Source Counts\nFiltered\nSample Trans\nFiltered Detected (Io)\nSample Detected (I)";
			newPlot.addLegend(legend);
			
			// update the keV limits for keV or Angstroms, keep the counts limits 
			newPlot.setLimits(kevList[0], kevList[kevList.length-1], oldPlotLimits[2], oldPlotLimits[3]);
			gPlotWindow.drawPlot(newPlot);
		}
	}

	//***************************************************************************************
	
	private void GetSelections(GenericDialog gd)
	{
		//General
		bhSet.plotChoice = gd.getNextRadioButton();

		//X-ray Source
		bhSet.target = gd.getNextChoice();
		bhSet.kv = gd.getNextNumber();
		bhSet.ma = gd.getNextNumber();

		//Filter
		bhSet.filter = gd.getNextChoice();
		bhSet.filterCM = gd.getNextNumber();
		bhSet.filterGmPerCC = mmc.getAtomGmPerCC(bhSet.filter);

		//Sample
		bhSet.sampleFormula = gd.getNextString();
		bhSet.sampleCM = gd.getNextNumber();
		bhSet.sampleGmPerCC = gd.getNextNumber();

		//Energy Range
		bhSet.kvStart = gd.getNextNumber();
		bhSet.kvEnd = gd.getNextNumber();
		bhSet.kvInc = gd.getNextNumber();

		//Detector
		bhSet.detFormula = gd.getNextString();
		bhSet.detCM = gd.getNextNumber();
		bhSet.detGmPerCC = gd.getNextNumber();
		
		bhSet.showResults = gd.getNextBoolean();
	}
	
	//***************************************************************************************
	
	public void actionPerformed(ActionEvent theEvent)
	{
		PlotSelections();
	}

	//***************************************************************************************

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent theEvent)
	{
		GetSelections(gd);
		return true;		
	}
}
