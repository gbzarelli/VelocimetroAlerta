/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.helpdev.supportlib_maps.gpx.objetos;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

/**
 *
 * @author Guilherme
 */
@Root(name = "TrackPointExtension")
public class TrackPointExtension {

    @Element(name = "atemp")
    private String atemp;
    @Element(name = "hr")
    private String hr;

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
        return "TrackPointExtension{" + "atemp=" + atemp + ", hr=" + hr + '}';
    }

}
