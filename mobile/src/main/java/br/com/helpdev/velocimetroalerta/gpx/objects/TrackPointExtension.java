/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.helpdev.velocimetroalerta.gpx.objects;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

import java.io.Serializable;

/**
 * @author Guilherme
 */
@Root(name = "gpxtpx:TrackPointExtension")
public class TrackPointExtension implements Serializable {

    @Element(name = "gpxtpx:atemp", required = false)
    private String atemp;
    @Element(name = "gpxtpx:hr", required = false)
    private String hr;
    @Element(name = "gpxtpx:cad", required = false)
    private String cad;
    @Element(name = "gpxtpx:rhu", required = false)
    private String rhu;

    public String getRhu() {
        return rhu;
    }

    public void setRhu(String rhu) {
        this.rhu = rhu;
    }

    public String getCad() {
        return cad;
    }

    public void setCad(String cad) {
        this.cad = cad;
    }

    public String getAtemp() {
        return atemp;
    }

    public void setAtemp(String atemp) {
        this.atemp = atemp;
    }

    public String getHr() {
        return hr;
    }

    public void setHr(String hr) {
        this.hr = hr;
    }

    @Override
    public String toString() {
        return "TrackPointExtension{" +
                "atemp='" + atemp + '\'' +
                ", hr='" + hr + '\'' +
                ", cad='" + cad + '\'' +
                ", rhu='" + rhu + '\'' +
                '}';
    }
}
