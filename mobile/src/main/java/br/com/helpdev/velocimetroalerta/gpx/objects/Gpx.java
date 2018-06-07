/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.helpdev.velocimetroalerta.gpx.objects;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;

/**
 * @author Guilherme
 */
@Root(name = "gpx", strict = false)
@Namespace(reference = "http://www.topografix.com/GPX/1/1")
@NamespaceList(
        value = {
                @Namespace(prefix = "gpxtpx", reference = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1")
                , @Namespace(prefix = "gpxx", reference = "http://www.garmin.com/xmlschemas/GpxExtensions/v3")
                , @Namespace(prefix = "xsi", reference = "http://www.w3.org/2001/XMLSchema-instance")
        }
)
public class Gpx {

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());

    @Attribute(name = "xsi:shemaLocation")
    private String schemaLocation = "http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd";

    @Attribute(name = "creator")
    private String creator;

    @Attribute(name = "version")
    private String version = "1.1";

    @Element(name = "metadata", type = MetaData.class)
    private MetaData metaData;

    @Element(name = "trk", type = Trk.class)
    private Trk trk;

    public Gpx() {
        trk = new Trk();
    }

    public Gpx(String creator) {
        this();
        this.creator = creator;
    }

    public MetaData getMetaData() {
        return metaData;
    }

    public void setMetaData(MetaData metaData) {
        this.metaData = metaData;
    }

    public Trk getTrk() {
        return trk;
    }

    public void setTrk(Trk trk) {
        this.trk = trk;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "Gpx{" + "schemaLocation=" + schemaLocation + ", creator=" + creator + ", version=" + version + ", metaData=" + metaData + ", trk=" + trk + '}';
    }

    public static String getUtcGpxTime(long data) {//2017-04-11T09:02:40Z
        SIMPLE_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
        return SIMPLE_DATE_FORMAT.format(new Date(data));
    }

}
