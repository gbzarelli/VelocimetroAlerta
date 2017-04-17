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
@Root(name = "metadata")
public class MetaData {

    @Element(name = "time")
    private String time;

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    @Override
    public String toString() {
        return "MetaData{" + "time=" + time + '}';
    }

}
