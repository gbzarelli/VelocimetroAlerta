/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.helpdev.velocimetroalerta.gpx.objects;


/**
 * @author Guilherme
 */
public class Extensions {
    public Extensions() {
    }

    public Extensions(TrackPointExtension trackPointExtension) {
        this.trackPointExtension = trackPointExtension;
    }

    private TrackPointExtension trackPointExtension;

    public TrackPointExtension getTrackPointExtension() {
        return trackPointExtension;
    }

    public void setTrackPointExtension(TrackPointExtension trackPointExtension) {
        this.trackPointExtension = trackPointExtension;
    }

    @Override
    public String toString() {
        return "Extensions{" + "trackPointExtension=" + trackPointExtension + '}';
    }

}
