package org.afritechinnovations.service.export;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.*;

/** Writes a streaming, macro-free SpreadsheetML workbook without retaining query results in memory. */
final class ExcelArchiveWriter implements AutoCloseable {
    private static final String MAIN="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL="http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private final ZipOutputStream zip;
    private final Writer xml;
    private final List<Sheet> sheets=new ArrayList<>();
    private final Path longTextFile;
    private final DataOutputStream longTexts;
    private String baseName, sheetName;
    private String[] headers;
    private int row, part;
    private boolean sheetOpen, finished, hasLongTexts;
    private record Sheet(String name,int rows,int columns) { }

    ExcelArchiveWriter(Path path) throws IOException {
        longTextFile=Files.createTempFile("fasoecole-texts-",".tmp");
        longTexts=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(longTextFile)));
        zip=new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path)),StandardCharsets.UTF_8);
        xml=new OutputStreamWriter(zip,StandardCharsets.UTF_8);
    }

    void sheet(String name,String... columns) throws IOException {
        closeSheet(); baseName=name; part=1; headers=columns; openSheet(name);
    }
    private void openSheet(String name) throws IOException {
        sheetName=name;row=1;sheetOpen=true;
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet"+(sheets.size()+1)+".xml"));
        xml.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\""+MAIN+"\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>");
        for(int c=0;c<headers.length;c++) xml.write("<col min=\""+(c+1)+"\" max=\""+(c+1)+"\" width=\""+(headers[c].matches(".*(Contenu|Commentaire|Description|Motif|Texte|Information).*")?60:28)+"\" customWidth=\"1\"/>");
        xml.write("</cols><sheetData><row r=\"1\" ht=\"28\" customHeight=\"1\">");
        for(int c=0;c<headers.length;c++) textCell(column(c)+"1",headers[c],1);
        xml.write("</row>");
    }
    void row(Object... values) throws IOException {
        if(!sheetOpen) throw new IllegalStateException("A worksheet must be opened first");
        if(values.length!=headers.length) throw new IllegalArgumentException("Export column count mismatch: "+sheetName);
        if(row==1_048_576) { closeSheet();part++;openSheet(baseName+" "+part); }
        row++; xml.write("<row r=\""+row+"\">");
        for(int c=0;c<values.length;c++) cell(column(c)+row,values[c],c);
        xml.write("</row>");
    }
    private void cell(String ref,Object value,int index) throws IOException {
        if(value==null) { xml.write("<c r=\""+ref+"\"/>");return; }
        if(value instanceof java.sql.Date d) value=d.toLocalDate();
        if(value instanceof java.sql.Timestamp t) value=t.toLocalDateTime();
        if(value instanceof Boolean b) { textCell(ref,b?"Oui":"Non",0);return; }
        if(value instanceof LocalDate d && !d.isBefore(LocalDate.of(1900,3,1))) {
            numberCell(ref,Long.toString(ChronoUnit.DAYS.between(LocalDate.of(1899,12,30),d)),2);return;
        }
        if(value instanceof LocalDateTime d && !d.toLocalDate().isBefore(LocalDate.of(1900,3,1))) {
            double days=ChronoUnit.DAYS.between(LocalDate.of(1899,12,30),d.toLocalDate())+d.toLocalTime().toNanoOfDay()/86_400_000_000_000.0;
            numberCell(ref,Double.toString(days),3);return;
        }
        if(value instanceof Number n && !(n instanceof BigDecimal d && d.precision()>15)
                && (!(n instanceof Long l) || Math.abs(l)<1_000_000_000_000_000L)) {
            numberCell(ref,n instanceof BigDecimal d?d.toPlainString():n.toString(),n instanceof BigDecimal?4:0);return;
        }
        String text=value.toString();
        if(text.length()>32_767) {
            String reference=sheetName+"!"+ref;
            int offset=0,chunk=1;
            while(offset<text.length()) {
                int end=Math.min(offset+30_000,text.length());
                if(end<text.length() && Character.isHighSurrogate(text.charAt(end-1))) end--;
                longTexts.writeUTF(reference);longTexts.writeUTF(headers[index]);longTexts.writeInt(chunk++);
                byte[] bytes=text.substring(offset,end).getBytes(StandardCharsets.UTF_8);
                longTexts.writeInt(bytes.length);longTexts.write(bytes);offset=end;
            }
            hasLongTexts=true;text="Texte intégral dans la feuille Textes longs : "+reference;
        }
        // All user text is an inline string, never an Excel formula (including leading =, +, - or @).
        textCell(ref,text,0);
    }
    private void textCell(String ref,String value,int style) throws IOException {
        xml.write("<c r=\""+ref+"\" s=\""+style+"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">");
        xml.write(escape(value));xml.write("</t></is></c>");
    }
    private void numberCell(String ref,String value,int style) throws IOException {
        xml.write("<c r=\""+ref+"\" s=\""+style+"\"><v>"+value+"</v></c>");
    }
    private void closeSheet() throws IOException {
        if(!sheetOpen) return;
        xml.write("</sheetData><autoFilter ref=\"A1:"+column(headers.length-1)+row+"\"/></worksheet>");
        xml.flush();zip.closeEntry();sheets.add(new Sheet(sheetName,row-1,headers.length));sheetOpen=false;
    }
    void finish() throws IOException {
        if(finished) return;
        longTexts.close();
        if(hasLongTexts) {
            sheet("Textes longs","Cellule source","Colonne","Partie","Texte intégral");
            try(var input=new DataInputStream(new BufferedInputStream(Files.newInputStream(longTextFile)))) {
                while(true) {
                    String reference;
                    try { reference=input.readUTF(); } catch(EOFException end) { break; }
                    String field=input.readUTF();int chunk=input.readInt();int size=input.readInt();
                    byte[] bytes=input.readNBytes(size);
                    if(bytes.length!=size) throw new EOFException("Incomplete export text");
                    row(reference,field,chunk,new String(bytes,StandardCharsets.UTF_8));
                }
            }
        }
        closeSheet();
        List<Sheet> summary=List.copyOf(sheets);
        sheet("Sommaire","Feuille","Nombre de lignes","Nombre de colonnes");
        for(Sheet s:summary) row(s.name(),s.rows(),s.columns());
        closeSheet();
        entry("_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\""+REL+"/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        StringBuilder workbook=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\""+MAIN+"\" xmlns:r=\""+REL+"\"><sheets>");
        StringBuilder relations=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        StringBuilder types=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for(int i=1;i<=sheets.size();i++) {
            workbook.append("<sheet name=\"").append(escape(sheets.get(i-1).name())).append("\" sheetId=\"").append(i).append("\" r:id=\"rId").append(i).append("\"/>");
            relations.append("<Relationship Id=\"rId").append(i).append("\" Type=\"").append(REL).append("/worksheet\" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
            types.append("<Override PartName=\"/xl/worksheets/sheet").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        relations.append("<Relationship Id=\"styles\" Type=\"").append(REL).append("/styles\" Target=\"styles.xml\"/>");
        entry("xl/workbook.xml",workbook.append("</sheets></workbook>").toString());
        entry("xl/_rels/workbook.xml.rels",relations.append("</Relationships>").toString());
        entry("[Content_Types].xml",types.append("</Types>").toString());
        entry("xl/styles.xml",STYLES);
        zip.finish();finished=true;
    }
    private void entry(String name,String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));xml.write(content);xml.flush();zip.closeEntry();
    }
    private static String column(int index) {
        StringBuilder name=new StringBuilder();
        for(int n=index+1;n>0;n=(n-1)/26) name.append((char)('A'+(n-1)%26));
        return name.reverse().toString();
    }
    private static String escape(String value) {
        StringBuilder clean=new StringBuilder();
        value.codePoints().forEach(cp -> {
            if(cp==9 || cp==10 || cp==13 || (cp>=32 && cp<=0xD7FF) || (cp>=0xE000 && cp<=0xFFFD) || (cp>=0x10000 && cp<=0x10FFFF)) clean.appendCodePoint(cp);
            else clean.append('\uFFFD');
        });
        return clean.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
    }
    @Override public void close() throws IOException {
        try { longTexts.close(); } finally { try { zip.close(); } finally { Files.deleteIfExists(longTextFile); } }
    }
    private static final String STYLES="""
        <?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <numFmts count="2"><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/><numFmt numFmtId="165" formatCode="dd/mm/yyyy hh:mm:ss"/></numFmts>
        <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font></fonts>
        <fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF19764F"/><bgColor indexed="64"/></patternFill></fill></fills>
        <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
        <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
        <cellXfs count="5">
        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"><alignment vertical="top" wrapText="1"/></xf>
        <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"><alignment vertical="center" wrapText="1"/></xf>
        <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
        <xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
        <xf numFmtId="2" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
        </cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>
        """;
}
