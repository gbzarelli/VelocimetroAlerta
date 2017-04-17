/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.helpdev.supportlib_maps.gpx.objetos;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

/**
 * @author Guilherme
 */
@Root(name = "gpx")
public class Gpx {

    @Attribute
    private String schemaLocation;

    @Attribute(name = "creator")
    private String creator;
    @Attribute(name = "version")
    private String version;
    @Element(name = "metadata", type = MetaData.class)
    private MetaData metaData;
    @Element(name = "trk", type = Trk.class)
    private Trk trk;

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
        return "Gpx{" + "creator=" + creator + ", version=" + version + ", metaData=" + metaData + ", trk=" + trk + '}';
    }

}
