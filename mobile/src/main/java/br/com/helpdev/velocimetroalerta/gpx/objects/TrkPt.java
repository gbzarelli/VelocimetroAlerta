/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.helpdev.velocimetroalerta.gpx.objects;


/**
 * @author Guilherme
 */
public class TrkPt {

    private String lat;
    private String lon;
    private double ele;
    private String time;
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
