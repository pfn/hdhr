package com.hanhuy.hdhr.config;

// hmm, treemodel... used for testing in main() only
import com.hanhuy.hdhr.treemodel.DeviceTreeModel;
import com.hanhuy.hdhr.treemodel.Tuner;

import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.StringReader;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.net.URL;
import java.net.MalformedURLException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathVariableResolver;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

import org.xml.sax.SAXException;
import org.xml.sax.InputSource;

public class Lineup {

    private String location;
    private Document document;
    private List<String> databaseIds;
    private final Map<String,String> lineupMap = new HashMap<String,String>();
    private final Map<String,Node> nodeMap = new HashMap<String,Node>();
    public final static String LINEUP_SERVER_LOCATION =
            "http://www.silicondust.com/hdhomerun/lineup_web/";
    private final XPath xpath;
    private final VariableResolver resolver;
    private final static String PROGRAM_PATH =
            "Program[PhysicalChannel = $channel and ProgramNumber = $program]";
    private final static String COUNT_PATH =
            "count(/LineupUIResponse/Lineup[DatabaseID = $databaseId]/Program)";
    private final static String LINEUP_PATH =
            "/LineupUIResponse/Lineup[DatabaseID = $databaseId]";
    private final static String DBID_PATH =
            "/LineupUIResponse/Lineup/DatabaseID";
    private final static String NAME_PATH = 
            "/LineupUIResponse/Lineup[DatabaseID = $databaseId]/DisplayName";
    private final XPathExpression PROGRAM_XPATH;
    private final XPathExpression COUNT_XPATH;
    private final XPathExpression LINEUP_XPATH;
    private final XPathExpression DBID_XPATH;
    private final XPathExpression NAME_XPATH;

    public Lineup(String location) {
        this.location = location;
        XPathFactory factory = XPathFactory.newInstance();
        resolver = new VariableResolver();
        factory.setXPathVariableResolver(resolver);
        xpath = factory.newXPath();

        try {
            PROGRAM_XPATH = xpath.compile(PROGRAM_PATH);
            COUNT_XPATH   = xpath.compile(COUNT_PATH);
            LINEUP_XPATH  = xpath.compile(LINEUP_PATH);
            DBID_XPATH    = xpath.compile(DBID_PATH);
            NAME_XPATH    = xpath.compile(NAME_PATH);
        }
        catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
    }

    private void fetch() throws IOException {
        if (document != null) return;
        InputStream in = null;
        try {
            URL u = new URL(LINEUP_SERVER_LOCATION + location);
            in = (InputStream) u.getContent();

            DocumentBuilder builder =
                    DocumentBuilderFactory.newInstance().newDocumentBuilder();
            document = builder.parse(new InputSource(in));
        }
        catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        catch (SAXException e) {
            throw new IllegalStateException(e);
        }
        finally {
            try {
                if (in != null)
                    in.close();
            }
            catch (IOException e) { }
        }
    }
    public String[] getDatabaseIDs() throws IOException {
        fetch();
        String[] databases;
        try {
            NodeList nodes = (NodeList) DBID_XPATH.evaluate(
                    document, XPathConstants.NODESET);

            databases = new String[nodes.getLength()];
            for (int i = 0, j = nodes.getLength(); i < j; i++) {
                Node node = nodes.item(i);
                String id = node.getTextContent();
                databases[i] = id;
                resolver.databaseId = id;
                String name = NAME_XPATH.evaluate(document);
                lineupMap.put(id, name);
    
            }
        }
        catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
        return databases;
    }

    public String getDisplayName(String id) {
        return lineupMap.get(id);
    }

    public int getProgramCount(String id) {
        int count = -1;
        try {
            resolver.databaseId = id;
            Number n = (Number) COUNT_XPATH.evaluate(
                    document, XPathConstants.NUMBER);
            count = n.intValue();
        }
        catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }

        return count;
    }

    public boolean applyProgramSettings(String id, Program program) {
        try {
            Node lineup;
            if (nodeMap.containsKey(id)) {
                lineup = nodeMap.get(id);
            } else {
                resolver.databaseId = id;
                lineup = (Node) LINEUP_XPATH.evaluate(
                        document, XPathConstants.NODE);
                nodeMap.put(id, lineup);
            }

            resolver.channel = program.channel.number;
            resolver.program = program.number;
            Element p = (Element) PROGRAM_XPATH.evaluate(
                    lineup, XPathConstants.NODE);
            if (p == null) return false;
            NodeList gn;
            gn = p.getElementsByTagName("Modulation");
            if (gn.getLength() > 0) {
                String pMod = program.channel.getModulation();
                String lMod = gn.item(0).getTextContent();
                if (!pMod.equals(lMod))
                    return false;
            }
            gn = p.getElementsByTagName("GuideName");
            if (gn.getLength() > 0)
                program.setName(gn.item(0).getTextContent());
            gn = p.getElementsByTagName("GuideNumber");
            if (gn.getLength() > 0) {
                String number = gn.item(0).getTextContent();
                if (!"".equals(number.trim())) {
                    int idx = number.indexOf(".");
                    if (idx != -1) {
                        program.virtualMajor = Short.parseShort(
                                number.substring(0, idx));
                        program.virtualMinor = Short.parseShort(
                                number.substring(idx + 1));
                    } else {
                        program.virtualMajor = Short.parseShort(number);
                    }
                }
            }
        }
        catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        FileInputStream fin =
                new FileInputStream(DeviceTreeModel.PROGRAM_FILE);
        ObjectInputStream ois = new ObjectInputStream(fin);
        Map<Tuner,List<Program>> programMap = (Map) ois.readObject();
        ois.close();

        Lineup l = new Lineup("US:95051");
        long start = System.currentTimeMillis();
        String id = l.getDatabaseIDs()[1];
        System.out.println("get ms: " + (System.currentTimeMillis() - start));

        int match = 0;
        start = System.currentTimeMillis();
        for (List<Program> programs : programMap.values()) {
            for (Program program : programs) {
                if (l.applyProgramSettings(id, program))
                    match++;
                else {
                    program.virtualMajor = 0;
                    program.virtualMinor = 0;
                    program.setName("UNKNOWN");
                }
                System.out.println(program);
            }
        }
        System.out.println("matches: " + match + ", ms: " +
                (System.currentTimeMillis() - start));

        for (String lid : l.getDatabaseIDs()) {
            for (List<Program> programs : programMap.values()) {
                match = 0;
                start = System.currentTimeMillis();
                for (Program program : programs) {
                    if (l.applyProgramSettings(lid, program))
                        match++;
                }
                System.out.println(lid + ": matches: " + match + ", ms: " +
                        (System.currentTimeMillis() - start));
            }
        }
    }

    static class VariableResolver implements XPathVariableResolver {
        public Object databaseId;
        public Object channel;
        public Object program;
        public Object resolveVariable(QName var) {
            String name = var.getLocalPart();
            if ("databaseId".equals(name))
                return databaseId;
            if ("channel".equals(name))
                return channel;
            if ("program".equals(name))
                return program;
            return null;
        }
    }
}
