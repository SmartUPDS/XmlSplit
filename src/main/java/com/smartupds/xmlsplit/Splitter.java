package com.smartupds.xmlsplit;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author Yannis Marketakis
 */
public class Splitter {
    static final CommandLineParser PARSER = new DefaultParser();
    static Options options = new Options();
    private String rootElementName;
    private String iterElementName;
    private long fileSize;
    private StringBuilder outputBuilder;
    private File originalFile;
    private static int newFileCounter=1;
    public static final Logger log=LogManager.getLogger(Splitter.class);
    
    public Splitter (File originalFile, String rootElem, String iterElem, double size){
        this.originalFile=originalFile;
        this.rootElementName=rootElem;
        this.iterElementName=iterElem;
        this.fileSize=Math.round(size*1024*1024);
        this.outputBuilder=new StringBuilder();
    }
            
    private static void createOptionsList(){
        Option fileOption = new Option("f", "file", true,"The XML file");
        fileOption.setRequired(true);
        
        Option rootElementOption = new Option("r", "root", true,"The name of the root element");
        rootElementOption.setRequired(true);
        
        Option iterElementOption = new Option("e", "element", true,"The name of the elements to split");
        iterElementOption.setRequired(true);
        
        Option sizeOption = new Option("s", "size", true,"The file size in MB");
        sizeOption.setRequired(true);
        
        options.addOption(fileOption)
               .addOption(rootElementOption)
               .addOption(iterElementOption)
               .addOption(sizeOption);
    }
    
    public void split(File exportFolder) throws SAXException, IOException, ParserConfigurationException, XPathExpressionException, TransformerException{
        DocumentBuilderFactory dbFactory=DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder=dbFactory.newDocumentBuilder();
        Document doc=dBuilder.parse(this.originalFile);
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true); // never forget this!

        XPathFactory xfactory = XPathFactory.newInstance();
        XPath xpath = xfactory.newXPath();
        XPathExpression allIterNodesExpression = xpath.compile("//"+this.rootElementName+"/"+this.iterElementName);
        NodeList iterNodes = (NodeList) allIterNodesExpression.evaluate(doc, XPathConstants.NODESET);
        log.info("Found "+iterNodes.getLength()+" distinct elements");
        
        int partialFileSize=0;
        Document partialXml = dBuilder.newDocument();
        Element newRootElement=partialXml.createElement(this.rootElementName);
        partialXml.appendChild(newRootElement);
        for(int i=0;i<iterNodes.getLength();i++){
            partialFileSize+=this.getElementSize(iterNodes.item(i));
            Node clonedNode=iterNodes.item(i).cloneNode(true);
            partialXml.adoptNode(clonedNode);
            newRootElement.appendChild(clonedNode);
            if(partialFileSize>this.fileSize){
                exportPartialFile(exportFolder,partialXml);
                partialXml=dBuilder.newDocument();
                newRootElement=partialXml.createElement(this.rootElementName);
                partialXml.appendChild(newRootElement);
                partialFileSize=0;
            }   
        }
        exportPartialFile(exportFolder,partialXml);   
    }
    
    private int getElementSize(Node node) throws TransformerConfigurationException, TransformerException{
        StringWriter writer = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString().length();
    }
    
    private void exportPartialFile(File exportFolder,Document doc) throws TransformerException{
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(doc);
        String filePath=this.originalFile.getName().replace(".xml", "-part_")+newFileCounter+".xml";
        if(exportFolder!=null){
            filePath=exportFolder.getAbsolutePath()+"/"+filePath;
        }
        File newFile=new File(filePath);
        StreamResult result =  new StreamResult(newFile);
        transformer.transform(source, result);
        log.info("Exported file "+newFile.getName());
        newFileCounter++;
            
    }
    
    public static void main(String []args) throws Exception{
        createOptionsList();
        
        CommandLine cli = PARSER.parse(options, args);
        Splitter splitter=new Splitter(new File(cli.getOptionValue("file")),cli.getOptionValue("root"), cli.getOptionValue("element"), Double.parseDouble(cli.getOptionValue("size")));
        splitter.split(null);
    }
}