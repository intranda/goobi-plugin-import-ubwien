package de.intranda.goobi.plugins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.goobi.production.enums.ImportReturnValue;
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
import ugh.exceptions.PreferencesException;
import ugh.exceptions.WriteException;
import de.sub.goobi.helper.exceptions.ImportPluginException;
import de.unigoettingen.sub.search.opac.ConfigOpac;
import de.unigoettingen.sub.search.opac.ConfigOpacCatalogue;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class MapImportPlugin implements IImportPlugin, IPlugin {

    private static final Logger logger = Logger.getLogger(MapImportPlugin.class);

    private static final String PLUGIN_NAME = "intranda_import_Maps";

    private Prefs prefs;

    private String tempFolder;

    private static final String SOURCE_FOLDER = "/home/tomcat/ubmaps/";

    private String currentIdentifier;

    @Override
    public String getProcessTitle() {
        return currentIdentifier;
    }

    @Override
    public List<ImportObject> generateFiles(List<Record> records) {
        List<ImportObject> answer = new ArrayList<ImportObject>();

        for (Record record : records) {
            ImportObject io = new ImportObject();
            io.setProcessTitle(record.getId());
            if (logger.isDebugEnabled()) {
                logger.debug("import data for " + record.getId());
            }
            currentIdentifier = record.getId();
            try {
                Fileformat ff = convertData();

                if (ff == null) {
                    io.setErrorMessage(record.getId() + ": error during opac request.");
                    io.setImportReturnValue(ImportReturnValue.InvalidData);
                } else {

                    // save mets file
                    try {
                        ff.write(tempFolder + record.getId() + ".xml");
                    } catch (WriteException | PreferencesException e) {
                        logger.error(e);
                    }
                    //            io.setImportFileName(importFolder + name + ".xml");
                    io.setMetsFilename(tempFolder + record.getId() + ".xml");
                    io.setProcessTitle(record.getId());
                    io.setImportReturnValue(ImportReturnValue.ExportFinished);

                    // copy image
                    File destination =
                            new File(tempFolder + record.getId() + File.separator + "images" + File.separator + "master_" + record.getId() + "_media"
                                    + File.separator + record.getId() + ".tif");
                    if (!destination.getParentFile().exists()) {
                        destination.getParentFile().mkdirs();
                    }
                    File source = new File(SOURCE_FOLDER + record.getData());
                    FileUtils.copyFile(source, destination);

                }

            } catch (ImportPluginException | IOException e) {
                logger.error(e);
                io.setErrorMessage(record.getId() + ": " + e.getMessage());
            }
            answer.add(io);
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
}
