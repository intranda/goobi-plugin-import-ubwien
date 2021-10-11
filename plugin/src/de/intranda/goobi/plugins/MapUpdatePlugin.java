package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.goobi.beans.Process;
import org.goobi.production.enums.ImportType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.importer.ImportObject;
import org.goobi.production.importer.Record;
import org.goobi.production.plugin.PluginLoader;
import org.goobi.production.plugin.interfaces.IImportPlugin;
import org.goobi.production.plugin.interfaces.IOpacPlugin;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.properties.ImportProperty;

import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.Prefs;
import ugh.exceptions.DocStructHasNoTypeException;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;
import de.sub.goobi.forms.MassImportForm;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class MapUpdatePlugin implements IImportPlugin, IPlugin {

    private static final Logger logger = Logger.getLogger(MapUpdatePlugin.class);

    private static final String PLUGIN_NAME = "intranda_update_Maps";

    private Prefs prefs;

    private String tempFolder;

    private static final String SOURCE_FOLDER = "/home/tomcat/ubmaps/";

    private String currentIdentifier;

    private MassImportForm form;

    @Override
    public String getProcessTitle() {
        return currentIdentifier;
    }

    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        List<ImportObject> answer = new ArrayList<ImportObject>();

        for (Record record : records) {
            try {
                form.addProcessToProgressBar();
                currentIdentifier = record.getId();
                Fileformat ff = convertData();
                if (ff == null) {
                    Helper.setFehlerMeldung("opac request error: " + currentIdentifier);
                } else {

                    List<Process> processList = ProcessManager.getProcesses(null, "prozesse.titel='" + currentIdentifier + "'");

                    if (processList.size() == 1) {
                        Process process = processList.get(0);
                        Fileformat metsfile = process.readMetadataFile();

                        DocStruct map = metsfile.getDigitalDocument().getLogicalDocStruct();
                        List<Metadata> oldData = map.getAllMetadata();
                        for (Metadata md : oldData) {
                            map.removeMetadata(md);
                        }
                        List<Metadata> newData = ff.getDigitalDocument().getLogicalDocStruct().getAllMetadata();
                        for (Metadata md : newData) {
                            map.addMetadata(md);
                        }

                        process.writeMetadataFile(metsfile);
                        Helper.setMeldung("import successfull: " + currentIdentifier);
                    }
                }
            } catch (ImportPluginException | PreferencesException | ReadException | WriteException | IOException | InterruptedException
                    | SwapException | DAOException | MetadataTypeNotAllowedException | DocStructHasNoTypeException e) {
                logger.error(e);
                Helper.setFehlerMeldung("import error: " + currentIdentifier);
            }
        }
        return answer;
    }

    @Override
    public List<ImportProperty> getProperties() {
        return null;
    }

    @Override
    public List<Record> generateRecordsFromFilenames(List<String> filenames) {
        List<Record> answer = new ArrayList<Record>();
        for (String filename : filenames) {
            Record record = new Record();
            record.setId(filename.replace(".tif", ""));
            record.setData(filename);
            answer.add(record);
        }

        return answer;
    }

    @Override
    public Fileformat convertData() throws ImportPluginException {
        if (logger.isDebugEnabled()) {
            logger.debug("Get opac record for " + currentIdentifier);
        }
        Fileformat ff = null;
        try {
            // get logical data from opac
            ConfigOpacCatalogue coc = new ConfigOpac().getCatalogueByName("OBVSG-MAP");
            IOpacPlugin myImportOpac = (IOpacPlugin) PluginLoader.getPluginByTitle(PluginType.Opac, coc.getOpacType());
            ff = myImportOpac.search("12", currentIdentifier, coc, prefs);

            // create physical image
            DigitalDocument dd = ff.getDigitalDocument();

            // create collection

            DocStruct log = dd.getLogicalDocStruct();
            Metadata col = new Metadata(prefs.getMetadataTypeByName("singleDigCollection"));
            col.setValue("Karten");
            log.addMetadata(col);

            DocStruct phys = dd.getPhysicalDocStruct();
            if (phys == null) {
                phys = dd.createDocStruct(prefs.getDocStrctTypeByName("BoundBook"));
                dd.setPhysicalDocStruct(phys);
            }
            //  imagepath
            Metadata path = new Metadata(prefs.getMetadataTypeByName("pathimagefiles"));
            path.setValue(currentIdentifier + "/images/" + currentIdentifier + "_media");
            phys.addMetadata(path);

            DocStruct page = dd.createDocStruct(prefs.getDocStrctTypeByName("page"));
            phys.addChild(page);
            page.setImageName(currentIdentifier + ".tif");

            Metadata logOrder = new Metadata(prefs.getMetadataTypeByName("logicalPageNumber"));
            logOrder.setValue("-");
            page.addMetadata(logOrder);

            Metadata physOrder = new Metadata(prefs.getMetadataTypeByName("physPageNumber"));
            physOrder.setValue("1");
            page.addMetadata(physOrder);

            phys.addChild(page);
        } catch (Exception e1) {
            logger.error(e1);
        }

        return ff;
    }

    @Override
    public void setImportFolder(String folder) {
        this.tempFolder = folder;

    }

    @Override
    public List<Record> splitRecords(String records) {
        return null;
    }

    @Override
    public List<Record> generateRecordsFromFile() {
        return null;
    }

    @Override
    public void setFile(File importFile) {
    }

    @Override
    public List<String> splitIds(String ids) {
        return null;
    }

    @Override
    public List<ImportType> getImportTypes() {
        List<ImportType> itl = new ArrayList<ImportType>();
        itl.add(ImportType.FOLDER);
        return itl;
    }

    @Override
    public List<String> getAllFilenames() {
        File folder = new File(SOURCE_FOLDER);
        String[] filenames = folder.list();
        List<String> files = Arrays.asList(filenames);
        Collections.sort(files);
        return files;
    }

    @Override
    public void deleteFiles(List<String> selectedFilenames) {
        //        for (String fileName : selectedFilenames) {
        //            File file = new File (SOURCE_FOLDER, fileName);
        //            file.delete();
        //        }
    }

    @Override
    public List<? extends DocstructElement> getCurrentDocStructs() {
        return null;
    }

    @Override
    public String deleteDocstruct() {
        return null;
    }

    @Override
    public String addDocstruct() {
        return null;
    }

    @Override
    public List<String> getPossibleDocstructs() {
        return null;
    }

    @Override
    public DocstructElement getDocstruct() {
        return null;
    }

    @Override
    public void setDocstruct(DocstructElement dse) {
    }

    @Override
    public PluginType getType() {
        return PluginType.Import;
    }

    @Override
    public String getTitle() {
        return PLUGIN_NAME;
    }

    @Override
    public String getDescription() {
        return PLUGIN_NAME;
    }

    @Override
    public void setPrefs(Prefs prefs) {
        this.prefs = prefs;
    }

    @Override
    public void setData(Record r) {
    }

    @Override
    public String getImportFolder() {
        return tempFolder;
    }

    public void setForm(MassImportForm form) {
        this.form = form;
    }
}
