package Services;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service class to combine two JSON files (sales_by_branch.json
 * and sales_by_productType.json) into a single Word document (.docx).
 */

public class LogsService {

    /**
     * Main conversion method to create a .docx file.
     *
     * @param branchJsonPath  Path to "sales_by_branch.json"
     * @param productJsonPath Path to "sales_by_productType.json"
     * @param outputDocxPath  Path to the resulting .docx file
     * @throws IOException if there's a read/write error
     */
    public static void convertToDoc(String branchJsonPath, String productJsonPath, String outputDocxPath)
            throws IOException {

        // Read JSON files
        String branchJson = readWholeFile(branchJsonPath);
        String productJson = readWholeFile(productJsonPath);

        // Generate the content for the Word document (document.xml)
        String documentXmlContent = generateDocumentXml(branchJson, productJson);

        // Create a .docx file by writing the required files into a ZIP archive
        try (FileOutputStream fos = new FileOutputStream(outputDocxPath);
                ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Add [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write(getContentTypesXml().getBytes());
            zos.closeEntry();

            // Add _rels/.rels
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            zos.write(getRelsXml().getBytes());
            zos.closeEntry();

            // Add word/document.xml
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write(documentXmlContent.getBytes());
            zos.closeEntry();

            // Add word/_rels/document.xml.rels
            zos.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
            zos.write(getDocumentRelsXml().getBytes());
            zos.closeEntry();
        }
    }

    /**
     * Reads the content of a file into a String.
     */
    private static String readWholeFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("File not found: " + path);
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    /**
     * Generates the content for the Word document (document.xml).
     */
    private static String generateDocumentXml(String branchJson, String productJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">\n");
        sb.append("<w:body>\n");

        // Title
        sb.append("<w:p><w:r><w:t>Sales Logs Conversion</w:t></w:r></w:p>\n");
        sb.append("<w:p><w:r><w:t>======================</w:t></w:r></w:p>\n");

        // Section 1: Sales By Branch
        sb.append("<w:p><w:r><w:t>1) Sales By Branch:</w:t></w:r></w:p>\n");
        sb.append("<w:p><w:r><w:t>--------------------------------</w:t></w:r></w:p>\n");
        sb.append(convertJsonToXml(branchJson));

        // Section 2: Sales By Product Type
        sb.append("<w:p><w:r><w:t>2) Sales By Product Type:</w:t></w:r></w:p>\n");
        sb.append("<w:p><w:r><w:t>--------------------------------</w:t></w:r></w:p>\n");
        sb.append(convertJsonToXml(productJson));

        sb.append("</w:body>\n");
        sb.append("</w:document>\n");
        return sb.toString();
    }

    /**
     * Converts JSON sales data to Word-compatible XML format.
     */
    private static String convertJsonToXml(String json) {
        StringBuilder sb = new StringBuilder();

        String[] lines = json.split("\n");
        for (String line : lines) {
            line = line.trim();
            sb.append("<w:p><w:r><w:t>").append(line).append("</w:t></w:r></w:p>\n");
        }

        return sb.toString();
    }

    /**
     * Returns the content of [Content_Types].xml for a docx file.
     */
    private static String getContentTypesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml" Extension="xml"/>
                  <Default ContentType="application/vnd.openxmlformats-package.relationships+xml" Extension="rels"/>
                </Types>""";
    }

    /**
     * Returns the content of _rels/.rels for a docx file.
     */
    private static String getRelsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                </Relationships>""";
    }

    /**
     * Returns the content of word/_rels/document.xml.rels for a docx file.
     */
    private static String getDocumentRelsXml() {
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                </Relationships>""";
    }
}
