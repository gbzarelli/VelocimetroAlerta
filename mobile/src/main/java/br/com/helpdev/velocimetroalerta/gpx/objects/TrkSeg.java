/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.helpdev.velocimetroalerta.gpx.objects;

import android.location.Location;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Guilherme
 */
@Root(name = "trkseg")
public class TrkSeg {

    public TrkSeg() {
        trkPts = new ArrayList<>();
    }

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

    public void addTrkPt(TrkPt trkPt) {
        trkPts.add(trkPt);
    }

    public void addTrkPts(List<Location> tempLocation) {
        for (Location loc : tempLocation) {
            addTrkPt(loc);
        }
    }

    public void addTrkPt(Location loc) {
        addTrkPt(loc, null);
    }

    public void addTrkPt(Location loc, TrackPointExtension trackPointExtension) {
        TrkPt trkPt = new TrkPt();
        trkPt.setLat(loc.getLatitude());
        trkPt.setLon(loc.getLongitude());
        trkPt.setEle(loc.getAltitude());
        trkPt.setAccuracy(loc.getAccuracy());
        trkPt.setTime(Gpx.getUtcGpxTime(loc.getTime()));
        if (null != trackPointExtension) {
            trkPt.setExtensions(new Extensions(trackPointExtension));
        }
        addTrkPt(trkPt);
    }
}
