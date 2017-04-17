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
@Root(name = "trkpt")
public class TrkPt {

    @Attribute(name = "lat")
    private String lat;
    @Attribute(name = "lon")
    private String lon;
    @Element(name = "ele")
    private String ele;
    @Element(name = "time")
    private String time;
    @Element(name = "extensions", type = Extensions.class, required = false)
    private Extensions extensions;

    public String getLat() {
        return lat;
    }

    public void setLat(String lat) {
        this.lat = lat;
    }

    public String getLon() {
        return lon;
    }

    public void setLon(String lon) {
        this.lon = lon;
    }

    public String getEle() {
        return ele;
    }

    public void setEle(String ele) {
        this.ele = ele;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public Extensions getExtensions() {
        return extensions;
    }

    public void setExtensions(Extensions extensions) {
        this.extensions = extensions;
    }

    @Override
    public String toString() {
        return "TrkPt{" + "lat=" + lat + ", lon=" + lon + ", ele=" + ele + ", time=" + time + ", extensions=" + extensions + '}';
    }

}
