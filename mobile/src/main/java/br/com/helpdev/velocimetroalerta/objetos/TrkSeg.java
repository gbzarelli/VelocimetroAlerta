/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.helpdev.supportlib_maps.gpx.objetos;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.List;

/**
 * @author Guilherme
 */
@Root(name = "trkseg")
public class TrkSeg {

    @ElementList(name = "trkpt",
            type = TrkPt.class,
            inline = true,
            required = false)
    private List<TrkPt> trkPts;

    public List<TrkPt> getTrkPts() {
        return trkPts;
    }

    public void setTrkPts(List<TrkPt> trkPts) {
        this.trkPts = trkPts;
    }

    @Override
    public String toString() {
        return "TrkSeg{" + "trkPts=" + trkPts + '}';
    }

}
