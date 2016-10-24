package br.com.helpdev.velocimetroalerta.gps;

import java.io.Serializable;

/**
 * Created by guilherme on 17/07/16.
 */
public class ObVelocimentroAlerta implements Serializable {
    private long tempo;
    private double vMedia;
    private double vAtual;
    private double vMaxima;
    private double distanciaTotal;
    private double altitude;
    private double ganhoAltitude;
    private double precisao;

    public ObVelocimentroAlerta() {
        this(0, 0, 0, 0, 0, 0, 0, 0);
    }

    public ObVelocimentroAlerta(long tempo, double vMedia, double vAtual, double vMaxima, double distanciaTotal, double altitude, double ganhoAltitude, double precisao) {
        this.tempo = tempo;
        this.vMedia = vMedia;
        this.vAtual = vAtual;
        this.vMaxima = vMaxima;
        this.distanciaTotal = distanciaTotal;
        this.altitude = altitude;
        this.ganhoAltitude = ganhoAltitude;
        this.precisao = precisao;
    }

    public double getPrecisao() {
        return precisao;
    }

    public void setPrecisao(double precisao) {
        this.precisao = precisao;
    }

    public long getTempo() {
        return tempo;
    }

    public void setTempo(long tempo) {
        this.tempo = tempo;
    }

    public double getvMedia() {
        return vMedia;
    }

    public void setvMedia(double vMedia) {
        this.vMedia = vMedia;
    }

    public double getvAtual() {
        return vAtual;
    }

    public void setvAtual(double vAtual) {
        this.vAtual = vAtual;
    }

    public double getvMaxima() {
        return vMaxima;
    }

    public void setvMaxima(double vMaxima) {
        this.vMaxima = vMaxima;
    }

    public double getDistanciaTotal() {
        return distanciaTotal;
    }

    public void setDistanciaTotal(double distanciaTotal) {
        this.distanciaTotal = distanciaTotal;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public double getGanhoAltitude() {
        return ganhoAltitude;
    }

    public void setGanhoAltitude(double ganhoAltitude) {
        this.ganhoAltitude = ganhoAltitude;
    }

    @Override
    public String toString() {
        return "ObVelocimentroAlerta{" +
                "tempo=" + tempo +
                ", vMedia=" + vMedia +
                ", vAtual=" + vAtual +
                ", vMaxima=" + vMaxima +
                ", distanciaTotal=" + distanciaTotal +
                ", altitude=" + altitude +
                ", ganhoAltitude=" + ganhoAltitude +
                ", precisao=" + precisao +
                '}';
    }
}
