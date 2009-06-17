package com.hanhuy.hdhr.config;

// hmm, treemodel... used for testing in main() only
import com.hanhuy.hdhr.treemodel.DeviceTreeModel;
import com.hanhuy.hdhr.treemodel.Tuner;

import com.hanhuy.hdhr.config.ChannelMap.Channel.Program;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Map;
import java.util.List;
import java.util.Date;
import java.util.TimeZone;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;

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
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

import org.xml.sax.SAXException;
import org.xml.sax.InputSource;

public class LineupServer {

    private final String location;
    private final int deviceId;
    private final String userUUID;
    private final static SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final static TimeZone tz = TimeZone.getTimeZone("UTC");

    public final static String LINEUP_SERVER_LOCATION =
            "https://www.silicondust.com/hdhomerun/lineupui?Cmd=";
    private final XPath xpath;
    private final VariableResolver resolver;
    private final static String PROGRAM_PATH =
            "Program[Frequency = $frequency and ProgramNumber = $program]";
    private final XPathExpression PROGRAM_XPATH;

    static {
        sdf.setTimeZone(tz);
    }

    public LineupServer(String location, int deviceId, String userUUID) {
        this.deviceId = deviceId;
        this.userUUID = userUUID;
        this.location = location;
        XPathFactory factory = XPathFactory.newInstance();
        resolver = new VariableResolver();
        factory.setXPathVariableResolver(resolver);
        xpath = factory.newXPath();

        try {
            PROGRAM_XPATH = xpath.compile(PROGRAM_PATH);
        }
        catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
    }

    private Document fetch(String command, Document doc) throws IOException {
        InputStream in = null;
        HttpURLConnection uc = null;
        String request = toXMLString(doc);
        try {
            URL u = new URL(LINEUP_SERVER_LOCATION + command);
            uc = (HttpURLConnection) u.openConnection();
            uc.setAllowUserInteraction(true);
            uc.setDoInput(true);
            uc.setDoOutput(true);
            uc.setRequestMethod("POST");
            OutputStreamWriter out = new OutputStreamWriter(
                    uc.getOutputStream());
            out.write(request, 0, request.length());
            out.close();
            in = uc.getInputStream();

            DocumentBuilder builder =
                    DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(new InputSource(in));

            return document;
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
            if (uc != null)
                uc.disconnect();
        }
    }

    private static Document newDocument() {
        Document document;
        try {
            DocumentBuilder builder =
                    DocumentBuilderFactory.newInstance().newDocumentBuilder();
            document = builder.newDocument();
        }
        catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        return document;
    }
    private Document buildIdentifyDocument(List<Program> programs) {

        Document document = newDocument();

        Node request = createRequestBody(document, "IdentifyPrograms2");
        for (Program p : programs) {
            long currentTime = p.channel.scanTime;
            if (currentTime == 0) currentTime = System.currentTimeMillis();
            Date date = new Date(currentTime);
            String ts = sdf.format(date);

            Node program = createElement(document, request, "Program", null);
            createElement(document, program,
                    "Modulation", p.channel.getModulation());
            createElement(document, program, "Frequency",
                    Integer.toString(p.channel.frequency));
            createElement(document, program, "TransportStreamID",
                    Integer.toString(p.channel.getTsID()));
            createElement(document, program, "ProgramNumber",
                    Integer.toString(p.number));
            createElement(document, program, "SeenTimestamp", ts);
        }

        return document;
    }

    private Node createRequestBody(Document d, String command) {
        Node request = createElement(
                d, d, "LineupUIRequest", null);

        createElement(d, request, "Vendor", "HanHuy");
        createElement(d, request, "Application", "HDHomeRun Browser");
        createElement(d, request, "Command", command);
        createElement(d, request, "UserID", userUUID);
        if (deviceId != 0) {
            createElement(d, request, "DeviceID",
                    Integer.toHexString(deviceId).toUpperCase());
        }
        createElement(d, request, "Location", location);
        return request;
    }

    private Node createElement(Document d, Node parent,
            String name, String value) {
        Node n = d.createElement(name);
        if (value != null) {
            Node text = d.createTextNode(value);
            n.appendChild(text);
        }
        parent.appendChild(n);
        return n;
    }

    private boolean identifyProgram(Document d, Program program)
    throws XPathExpressionException {
        Node lineup = d.getDocumentElement();
        resolver.frequency = program.channel.frequency;
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
        return true;
    }

    public void updatePrograms(List<Program> programs)
    throws TunerLineupException {
        Document request = newDocument();
        Node body = createRequestBody(request, "IdentifyFeedback2");

        long currentTime = System.currentTimeMillis();
        Date date = new Date(currentTime);
        String ts = sdf.format(date);
        for (Program p : programs) {
            Node program = createElement(request, body, "Program", null);
            createElement(request, program, "Modulation",
                    p.channel.getModulation());
            createElement(request, program, "Frequency",
                    Integer.toString(p.channel.frequency));
            createElement(request, program, "TransportStreamID",
                    Integer.toString(p.channel.getTsID()));
            createElement(request, program, "ProgramNumber",
                    Integer.toString(p.number));
            createElement(request, program, "UserGuideName", p.getName());
            if (p.virtualMajor != 0) {
                createElement(request, program, "UserGuideNumber",
                        p.getGuideNumber());
            }
            createElement(request, program, "UserModified", ts);
        }

        try {
            Document response = fetch("IdentifyFeedback2", request);
            Element root = response.getDocumentElement();
            NodeList children = root.getElementsByTagName("Command");
            if (children.getLength() != 1 || !"IdentifyFeedback2".equals(
                    children.item(0).getTextContent())) {
                throw new TunerLineupException(
                        "Received an unexpected response: " +
                        toXMLString(response));
            }
        }
        catch (IOException e) {
            throw new TunerLineupException(e);
        }
    }

    public int identifyPrograms(List<Program> programs)
    throws TunerLineupException {
        int count = 0;
        try {
            Document request = buildIdentifyDocument(programs);
            Document response = fetch("IdentifyPrograms2", request);
            for (Program p : programs) {
                if (identifyProgram(response, p))
                    count++;
            }
        }
        catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
        catch (IOException e) {
            throw new TunerLineupException(e);
        }
        return count;
    }

    static String toXMLString(Document d) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer t = tf.newTransformer();
            t.setOutputProperty("indent", "yes");
            DOMSource ds = new DOMSource(d);
            StringWriter sw = new StringWriter();
            StreamResult sr = new StreamResult(sw);
            t.transform(ds, sr);
            return sw.toString();
        }
        catch (TransformerConfigurationException e) {
            throw new IllegalStateException(e);
        }
        catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        FileInputStream fin =
                new FileInputStream(DeviceTreeModel.PROGRAM_FILE);
        ObjectInputStream ois = new ObjectInputStream(fin);
        Map<Tuner,List<Program>> programMap = (Map) ois.readObject();
        ois.close();

        LineupServer l = new LineupServer("US:95051", 0, "hdhr.configtestuser");
        long start = System.currentTimeMillis();
        int match = 0;
        start = System.currentTimeMillis();
        for (Object key : programMap.keySet()) {
            if (!key.toString().endsWith("0")) continue;

            List<Program> programs = programMap.get(key);
            int count = l.identifyPrograms(programs);
            Program last = null;
            for (Program program : programs) {
                System.out.println(program);
                last = program;
            }

            l.updatePrograms(java.util.Arrays.asList(last));
            System.out.println("matches: " + count + ", ms: " +
                    (System.currentTimeMillis() - start));
        }
    }

    static class VariableResolver implements XPathVariableResolver {
        public Object frequency;
        public Object program;
        public Object resolveVariable(QName var) {
            String name = var.getLocalPart();
            if ("frequency".equals(name))
                return frequency;
            if ("program".equals(name))
                return program;
            return null;
        }
    }
}
