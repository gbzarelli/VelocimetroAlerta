package br.com.helpdev.velocimetroalerta.gps;

import android.os.SystemClock;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * Created by guilherme on 17/07/16.
 */
public class ObVelocimentroAlerta implements Serializable {

    private Date dateInicio;
    private double vMedia;
    private double vAtual;
    private double vMaxima;
    private double distanciaTotal;
    private double altitude;
    private double ganhoAltitude;
    private double ganhoAltitudeNegativa;
    private double precisao;
    private long firstBase;
    private long baseTime;
    private long timePaused;
    private double distanciaPausada;

    public ObVelocimentroAlerta() {
        this(new Date(), 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public ObVelocimentroAlerta(Date dateInicio, double vMedia, double vAtual, double vMaxima, double distanciaTotal, double altitude, double ganhoAltitude, double ganhoAltitudeNegativa, double precisao) {
        this.dateInicio = dateInicio;
        this.vMedia = vMedia;
        this.vAtual = vAtual;
        this.vMaxima = vMaxima;
        this.distanciaTotal = distanciaTotal;
        this.altitude = altitude;
        this.ganhoAltitude = ganhoAltitude;
        this.ganhoAltitudeNegativa = ganhoAltitudeNegativa;
        this.precisao = precisao;
    }

    public double getDistanciaPausada() {
        return distanciaPausada;
    }

    public void setDistanciaPausada(double distanciaPausada) {
        this.distanciaPausada = distanciaPausada;
    }

    public void addDistanciaPausada(double distanciaPausada) {
        this.distanciaPausada += distanciaPausada;
    }

    public long getFirstBase() {
        return firstBase;
    }

    public void setFirstBase(long firstBase) {
        this.firstBase = firstBase;
    }

    public long getBaseTime() {
        return baseTime;
    }

    public void setBaseTime(long baseTime) {
        this.baseTime = baseTime;
    }

    public long getTimePaused() {
        return timePaused;
    }

    public void setTimePaused(long timePaused) {
        this.timePaused = timePaused;
    }

    public double getGanhoAltitudeNegativa() {
        return ganhoAltitudeNegativa;
    }

    public void setGanhoAltitudeNegativa(double ganhoAltitudeNegativa) {
        this.ganhoAltitudeNegativa = ganhoAltitudeNegativa;
    }

    public void addGanhoAltitudeNegativa(double ganhoAltitudeNegativa) {
        this.ganhoAltitudeNegativa += ganhoAltitudeNegativa;
    }

    public Date getDateInicio() {
        return dateInicio;
    }

    public void setDateInicio(Date dateInicio) {
        this.dateInicio = dateInicio;
    }

    public double getPrecisao() {
        return precisao;
    }

    public void setPrecisao(double precisao) {
        this.precisao = precisao;
    }

    public double getvMedia() {
        double hours = new BigDecimal(getTempoAtividade())
                .divide(BigDecimal.valueOf(3_600_000), 10, BigDecimal.ROUND_HALF_UP)
                .doubleValue();
        vMedia = (getDistanciaTotal() - getDistanciaPausada()) / hours;
        return vMedia;
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

    public void addDistancia(double distancia) {
        this.distanciaTotal += distancia;
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

    public void addGanhoAltitude(double ganhoAltitude) {
        this.ganhoAltitude += ganhoAltitude;
    }

    /**
     * @return Dureção em millis
     */
    public long getTempoAtividade() {
        return SystemClock.elapsedRealtime() - baseTime;
    }

    @Override
    public String toString() {
        return "ObVelocimentroAlerta{" +
                "dateInicio=" + dateInicio +
                ", vMedia=" + vMedia +
                ", vAtual=" + vAtual +
                ", vMaxima=" + vMaxima +
                ", distanciaTotal=" + distanciaTotal +
                ", altitude=" + altitude +
                ", ganhoAltitude=" + ganhoAltitude +
                ", ganhoAltitudeNegativa=" + ganhoAltitudeNegativa +
                ", precisao=" + precisao +
                ", firstBase=" + firstBase +
                ", baseTime=" + baseTime +
                ", timePaused=" + timePaused +
                '}';
    }
}
