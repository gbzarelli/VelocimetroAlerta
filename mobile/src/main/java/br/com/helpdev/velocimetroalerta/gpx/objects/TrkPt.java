/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.helpdev.velocimetroalerta.gpx.objects;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

/**
 * @author Guilherme
 */
@Root(name = "trkpt", strict = false)
public class TrkPt {

    @Attribute(name = "lat")
    private String lat;
    @Attribute(name = "lon")
    private String lon;
    @Element(name = "ele")
    private double ele;
    @Element(name = "time")
    private String time;
    @Element(name = "extensions", type = Extensions.class, required = false)
    private Extensions extensions;

    private double latitude;
    private double longitude;
    private double accuracy;

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public String getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.latitude = lat;
        this.lat = String.valueOf(lat);
    }

    public String getLon() {
        return lon;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLon(double lon) {
        this.longitude = lon;
        this.lon = String.valueOf(lon);
    }

    public double getEle() {
        return ele;
    }

    public void setEle(double ele) {
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
