package CT_Tools;
/*
 * An example plugin that calls several of the functions available in MuMassCalc_J8Lib.jar
 * MuMassCalc_J8Lib.jar is derived from the NIST XCOM web application used to calculate 
 * photon cross sections for scattering, photoelectric absorption and pair production,
 * as well as total attenuation coefficients, for any element, compound (Z less than o equal to 100),
 * at energies from 1 keV to 100 GeV.
 * See: https://github.com/LazzyIzzi/MuMassCalculator
 * 
 * This plugin supports two operations:
 * 1. Interactive calculation of interpolated attenuation cross-sections in units of cm2/gm from a
 * user-supplied simplified chemical formula and the photon energy. Calculated values 
 * are posted to a results window.
 * 
 * 2.Plotting of tabulated absorption spectra for selected cross-sections for the user supplied
 * formula and energy range.  Optionally the absorption edge energies for the atoms in the formula
 * are reported in a separate results table.
 * 
 */

import java.awt.*;
import java.awt.event.*;
import java.util.*;

import ij.plugin.*;
import ij.gui.*;
import ij.*;
import ij.measure.*;

import jhd.MuMassCalculator.*;
import tagTools.common.*;


public class Xray_Lookup implements PlugIn, ActionListener, DialogListener
{

	MuMassCalculator mmc= new MuMassCalculator();
	MatlListTools2 mlt=new MatlListTools2();
	MatlListTools2.TagSet tagSet;
	String[] matlName;
	String[] matlFormula;
	double[] matlGmPerCC;
	
	String myDialogTitle = "X-Ray Lookup";
	GenericDialog gd;
	int dlogW,dlogH,dlogL,dlogT;
	
	PlotWindow gPlotWindow;
	final int resultW=600,resultH=190,calcW=900,calcH=230;
	final Color buff = new Color(250,240,200);
		
	//Local use variables, not used by MuMassCalculator
	String name,nameR1,nameR2;

	protected class MMCsettings
	{
		//String formulaMev;
		//double meV;
		
		String formula;
		double attn;
		double gmPerCC;
		
		String formulaR1;
		double attnR1;
		double gmPerCCR1;
		
		String formulaR2;
		double attnR2;
		double gmPerCCR2;
	}
	
	MMCsettings mmcSet = new MMCsettings();
	
	//*********************************************************************************/
	
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
		DoDialog();
		
//		String dir = IJ.getDirectory("plugins");
//		dir= dir.replace("\\","/");
//		String myURL = "file:///" + dir + "jars/MuMassCalculatorDocs/index.html";
	}

	//*********************************************************************************/

	public void DoDialog()
	{
		
		Font myFont = new Font(Font.DIALOG, Font.ITALIC+Font.BOLD, 14);
		InitSettings();
		
		//ShowSelections();
		gd =  GUI.newNonBlockingDialog(myDialogTitle);
		
		gd.addMessage("Energy Solver",myFont);
		
		gd.addMessage("___________________________________________",myFont);
		gd.addMessage("Get Energy from linear attenuation cm2/gm",myFont);
		gd.addChoice("Material Name", matlName, matlName[0]); //myTags.matlName[0] is column header title
		gd.setName("MatlNameMenu");
		
		gd.addStringField("Formula: ", mmcSet.formula,26);
		gd.addNumericField("Observed cm2/gm for Formula 1", mmcSet.attn);
		gd.addNumericField("Observed density for Formula 1", mmcSet.gmPerCC);
		gd.setInsets(0, 250, 0);
		gd.addButton("Get KeV", this);

		gd.addMessage("___________________________________________",myFont);
		gd.addMessage("Get Energy from linear attenuation ratio",myFont);
		gd.addChoice("Material Name 1", matlName, matlName[0]); //myTags.matlName[0] is column header title
		gd.addStringField("Formula 1: ", mmcSet.formulaR1,26);
		//gd.addStringField("Formula 1: ", mmcSet.formulaR1,26);
		gd.addNumericField("Observed brightness for Formula 1", mmcSet.attnR1);
		gd.addNumericField("Observed density for Formula 1", mmcSet.gmPerCCR1);
		
		gd.addChoice("Material Name 2", matlName, matlName[0]); //myTags.matlName[0] is column header title
		gd.addStringField("Formula 2: ", mmcSet.formulaR2,26);
		gd.addNumericField("Observed brightness for Formula 2", mmcSet.attnR2);
		gd.addNumericField("Observed density for Formula 2",  mmcSet.gmPerCCR2);
		gd.setInsets(0, 250, 0);
		gd.addButton("Get KeV from ratio", this);
		gd.addHelp("https://lazzyizzi.github.io/XrayLookup.html");
		
		gd.addDialogListener(this);
		//gd.addHelp(myURL);
		
		//Rename the Dialogs components to make it easier to fetch the settings
		@SuppressWarnings("unchecked")		
		Vector<Choice> ch = gd.getChoices();
		ch.get(0).setName("nameChoice");
		ch.get(1).setName("name1Choice");
		ch.get(2).setName("name2Choice");
		
		@SuppressWarnings("unchecked")		
		Vector<TextField> tfv = gd.getStringFields();
		tfv.get(0).setName("formula");
		tfv.get(1).setName("formulaR1");
		tfv.get(2).setName("formulaR2");

		@SuppressWarnings("unchecked")		
		Vector<TextField> ntfv = gd.getNumericFields();
		ntfv.get(0).setName("attn");
		ntfv.get(1).setName("gmPerCC");
		ntfv.get(2).setName("attnR1");
		ntfv.get(3).setName("gmPerCCR1");
		ntfv.get(4).setName("attnR2");
		ntfv.get(5).setName("gmPerCCR2");
		
		gd.showDialog();

		if (gd.wasCanceled())
		{
			if(gPlotWindow!=null) gPlotWindow.close();
			ResultsTable rt;
			rt = ResultsTable.getResultsTable("KeV Solutions");
			if(rt!=null)
			{			
				IJ.selectWindow("KeV Solutions");
				IJ.run("Close");
			}
			return;
		}
//		if (gd.wasOKed())
//		{
//			gd.dispose();
//			return;
//		}

	}

	//*********************************************************************************/

	private void InitSettings()
	{
		mmcSet.formula= matlFormula[0];
		mmcSet.attn =0;
		mmcSet.gmPerCC = matlGmPerCC[0];
		name= matlName[0];
		
		mmcSet.formulaR1= matlFormula[0];
		mmcSet.attnR1= 0;
		mmcSet.gmPerCCR1 = matlGmPerCC[0];
		nameR1= matlName[0];
		
		mmcSet.formulaR2= matlFormula[0];
		mmcSet.attnR2 = 0;
		mmcSet.gmPerCCR2 =matlGmPerCC[0];
		nameR2= matlName[0];

	}
	
	//*********************************************************************************/
	
	private boolean ValidateParams()
	{
		boolean paramsOK = true;
		//check the user formulas for validity
		if(mmc.getMevArray(mmcSet.formula)==null)
		{
			IJ.showMessage("Error", mmcSet.formula + " Bad Formula, Element or count missing");
			paramsOK = false;
		}		
		if(mmc.getMevArray(mmcSet.formulaR1)==null)
		{
			IJ.showMessage("Error", mmcSet.formulaR1 + " Bad Formula, Element or count missing");
			paramsOK = false;
		}
		if(mmc.getMevArray(mmcSet.formulaR2)==null)
		{
			IJ.showMessage("Error", mmcSet.formulaR2 + " Bad Formula, Element or count missing");
			paramsOK = false;
		}
		
		return paramsOK;
	}

	//*********************************************************************************/

	@SuppressWarnings("unchecked")
	private boolean GetSelections()
	{

		try
		{
			Vector<Choice> choices= gd.getChoices();
			name = matlName[choices.get(0).getSelectedIndex()];
			nameR1 = matlName[choices.get(1).getSelectedIndex()];
			nameR2 = matlName[choices.get(2).getSelectedIndex()];
			
			Vector<TextField> textFields = gd.getStringFields();
			mmcSet.formula = textFields.get(0).getText();
			mmcSet.formulaR1 = textFields.get(1).getText();
			mmcSet.formulaR2 = textFields.get(2).getText();
			
			String str;
			Vector<TextField> numberFields = gd.getNumericFields();
			str = numberFields.get(0).getText();
			if(isNumeric(str)) mmcSet.attn = Double.parseDouble(str);

			str = numberFields.get(1).getText();
			if(isNumeric(str)) mmcSet.gmPerCC = Double.parseDouble(str);
			
			str = numberFields.get(2).getText();
			if(isNumeric(str)) mmcSet.attnR1 = Double.parseDouble(str);

			str = numberFields.get(3).getText();
			if(isNumeric(str)) mmcSet.gmPerCCR1 = Double.parseDouble(str);

			str = numberFields.get(4).getText();
			if(isNumeric(str)) mmcSet.attnR2 = Double.parseDouble(str);

			str = numberFields.get(5).getText();
			if(isNumeric(str)) mmcSet.gmPerCCR2 = Double.parseDouble(str);
			return true;
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			return false;
		}		
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

	//*********************************************************************************/
	
	private void UpdateResults1(double[] result)
	{
		dlogW = gd.getSize().width;
		dlogH = gd.getSize().height;
		dlogL = gd.getLocation().x;
		dlogT = gd.getLocation().y;
		ResultsTable rt = ResultsTable.getResultsTable("KeV Solutions");
		if(rt==null || rt.getCounter()==0)
		{
			rt=new ResultsTable();
			rt.setPrecision(5);
			rt.addValue("Name 1","");
			rt.addValue("Formula 1","");
			rt.addValue("gm/cc 1","");
			rt.addValue("Measure 1","");
			rt.addValue("Name 2","");
			rt.addValue("Formula 2","");
			rt.addValue("gm/cc 2","");
			rt.addValue("Measure 2","");
			rt.deleteRow(0);
		}
		
		rt.incrementCounter();
		if(result!=null)
		{
			for(int i=0;i<result.length;i++)
			{
				rt.addValue("KeV " + i , result[i]*1000);
			}			
		}
		else
		{
			rt.addValue("KeV 0", "No Solution");
		}
		rt.addValue("Name 1", name);
		rt.addValue("Formula 1", mmcSet.formula);
		rt.addValue("Measure 1", mmcSet.attn);
		rt.addValue("gm/cc 1", mmcSet.gmPerCC);

		rt.addValue("Name 2", "N/A");
		rt.addValue("Formula 2", "N/A");
		rt.addValue("Measure 2", "N/A");
		rt.addValue("gm/cc 2", "N/A");
		rt.show("KeV Solutions");
		Window win = WindowManager.getWindow("KeV Solutions");
		win.setLocation(dlogL+dlogW,dlogT);
		//win.setForeground(buff);
		//ResultsTable.getResultsTable("KeV Solutions").setBackground(buff);
		//win.setSize(calcW,calcH);
		win.setSize(calcW,dlogH);
	}
	
	//*********************************************************************************/	


	private void UpdateResults2(double[] result)
	{
		dlogW = gd.getSize().width;
		dlogH = gd.getSize().height;
		dlogL = gd.getLocation().x;
		dlogT = gd.getLocation().y;

		ResultsTable rt = ResultsTable.getResultsTable("KeV Solutions");
		if(rt==null || rt.getCounter()==0)
		{
			rt=new ResultsTable();
			rt.setPrecision(5);
			rt.addValue("Name 1","");
			rt.addValue("Formula 1","");
			rt.addValue("gm/cc 1","");
			rt.addValue("Measure 1","");
			rt.addValue("Name 2","");
			rt.addValue("Formula 2","");
			rt.addValue("gm/cc 2","");
			rt.addValue("Measure 2","");
			rt.deleteRow(0);
		}

		rt.incrementCounter();
		if(result!=null)
		{
			for(int i=0;i<result.length;i++)
			{
				rt.addValue("KeV " + i , result[i]*1000);
			}			
		}
		else
		{
			rt.addValue("KeV 0", "No Solution");
		}
		
		rt.addValue("Name 1", nameR1);
		rt.addValue("Formula 1", mmcSet.formulaR1);
		rt.addValue("Measure 1", mmcSet.attnR1);
		rt.addValue("gm/cc 1", mmcSet.gmPerCCR1);
		rt.addValue("Name 2", nameR2);
		rt.addValue("Formula 2", mmcSet.formulaR2);
		rt.addValue("Measure 2", mmcSet.attnR2);
		rt.addValue("gm/cc 2", mmcSet.gmPerCCR2);
		rt.show("KeV Solutions");
		Window win = WindowManager.getWindow("KeV Solutions");
		win.setLocation(dlogL+dlogW,dlogT);
		win.setForeground(buff);
		//win.setSize(calcW,calcH);
		win.setSize(calcW,dlogH);
	}

	//*********************************************************************************/

	public void actionPerformed(ActionEvent theEvent)
	{
		//GetSelections();
		if(GetSelections() && ValidateParams())
		{
			String cmd = theEvent.getActionCommand();
			switch(cmd)
			{
			case "Get KeV":
				double[] result1 = mmc.getMeVfromMuLin(mmcSet.formula, mmcSet.attn, mmcSet.gmPerCC, "TotAttn");
				UpdateResults1(result1);
				break;
			case "Get KeV from ratio":
				//ShowSelections();
				double[] result2 = mmc.getMeVfromMuLinRatio(mmcSet.formulaR1, mmcSet.attnR1, mmcSet.gmPerCCR1, mmcSet.formulaR2, mmcSet.attnR2, mmcSet.gmPerCCR2, "TotAttn");
				UpdateResults2(result2);
				break;
			}
		}
	}

	//*********************************************************************************/
	
	@SuppressWarnings("unchecked")		
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
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
			case "nameChoice":
				chi  = chv.get(0).getSelectedIndex();
				tfi = GetTextFieldIndex(tfv,"formula");
				ntfi = GetTextFieldIndex(ntfv,"gmPerCC");
				if(tfi>=0)tfv.get(tfi).setText(matlFormula[chi]);
				if(ntfi>=0)ntfv.get(ntfi).setText(Double.toString(matlGmPerCC[chi]));
				break;
			case "name1Choice":
				chi  = chv.get(1).getSelectedIndex();
				tfi = GetTextFieldIndex(tfv,"formulaR1");
				ntfi = GetTextFieldIndex(ntfv,"gmPerCCR1");
				if(tfi>=0)tfv.get(tfi).setText(matlFormula[chi]);
				if(ntfi>=0)ntfv.get(ntfi).setText(Double.toString(matlGmPerCC[chi]));
				break;
			case "name2Choice":
				chi  = chv.get(2).getSelectedIndex();
				tfi = GetTextFieldIndex(tfv,"formulaR2");
				ntfi = GetTextFieldIndex(ntfv,"gmPerCCR2");
				if(tfi>=0)tfv.get(tfi).setText(matlFormula[chi]);
				if(ntfi>=0)ntfv.get(ntfi).setText(Double.toString(matlGmPerCC[chi]));
				break;
			}
			//return true;
		}
//		else
//		{
//			return false; disables ok button
//		}
		return true;
	}
	
	//*********************************************************************************/
	
	/**Java should have a method like this!!
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

//	private void ShowSelections()
//	{
//		IJ.log("***************************************");
//		
//		IJ.log("mmcSet.formulaMuLin=" + mmcSet.formula);
//		IJ.log("mmcSet.attnMuLin=" + mmcSet.attn);
//		IJ.log("mmcSet.gmPerCCMuLin=" + mmcSet.gmPerCC);
//
//		IJ.log("mmcSet.formulaMuLin=" + mmcSet.formula);
//		IJ.log("mmcSet.attnMuLin=" + mmcSet.attn);
//		IJ.log("mmcSet.gmPerCCMuLin=" + mmcSet.gmPerCC);
//		
//		IJ.log("mmcSet.formulaR1=" + mmcSet.formulaR1);
//		IJ.log("mmcSet.attnR1=" + mmcSet.attnR1);
//		IJ.log("mmcSet.gmPerCCR1=" + mmcSet.gmPerCCR1);
//		
//		
//		IJ.log("mmcSet.formulaR2=" + mmcSet.formulaR2);
//		IJ.log("mmcSet.attnR2=" + mmcSet.attnR2);
//		IJ.log("mmcSet.gmPerCCR2=" + mmcSet.gmPerCCR2);
//	}
}
