/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.helpdev.supportlib_maps.gpx.objetos;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;


/**
 * @author Guilherme
 */
@Root(name = "trk")
public class Trk {

    @Element(name = "name")
    private String name;
    @Element(name = "trkseg", type = TrkSeg.class)
    private TrkSeg trkseg;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TrkSeg getTrkseg() {
        return trkseg;
    }

    public void setTrkseg(TrkSeg trkseg) {
        this.trkseg = trkseg;
    }

    @Override
    public String toString() {
        return "Trk{" + "name=" + name + ", trkseg=" + trkseg + '}';
    }

}
