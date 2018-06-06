package br.com.helpdev.velocimetroalerta.gps;

import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by guilherme on 17/07/16.
 */
public class ObVelocimentroAlerta implements Serializable {

    private Date dateInicio;
    private double vMedia;
    private double vAtual;
    private double vMaxima;
    private double distancia;
    private double distanciaPausada;
    private double altitudeAtual;
    private double precisaoAtual;
    private double ganhoAltitude;
    private double perdaAltitude;
    private long duracao;
    private long duracaoPausado;
    private int cadence;
    private int bpm;
    private int temperature;
    private int humidity;

    public ObVelocimentroAlerta() {
        this.dateInicio = new Date();
    }

    public long getDuracaoPausado() {
        return duracaoPausado;
    }

    public void setDuracaoPausado(long duracaoPausado) {
        this.duracaoPausado = duracaoPausado;
    }

    public void addDuracaoPausado(double duracaoPausado) {
        this.duracaoPausado += duracaoPausado;
    }

    public double getDuracao() {
        return duracao;
    }

    public void setDuracao(long duracao) {
        this.duracao = duracao;
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

    public int getCadence() {
        return cadence;
    }

    public void setCadence(int cadence) {
        this.cadence = cadence;
    }

    public int getBpm() {
        return bpm;
    }

    public void setBpm(int bpm) {
        this.bpm = bpm;
    }

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    public int getHumidity() {
        return humidity;
    }

    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }

    public double getPerdaAltitude() {
        return perdaAltitude;
    }

    public void setPerdaAltitude(double perdaAltitude) {
        this.perdaAltitude = perdaAltitude;
    }

    public void addPerdaAltitude(double perdaAltitude) {
        this.perdaAltitude += perdaAltitude;
    }

    public Date getDateInicio() {
        return dateInicio;
    }

    public void setDateInicio(Date dateInicio) {
        this.dateInicio = dateInicio;
    }

    public double getPrecisaoAtual() {
        return precisaoAtual;
    }

    public void setPrecisaoAtual(double precisaoAtual) {
        this.precisaoAtual = precisaoAtual;
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

    public double getDistancia() {
        return distancia;
    }

    public void setDistancia(double distancia) {
        this.distancia = distancia;
    }

    public void addDistancia(double distancia) {
        this.distancia += distancia;
    }

    public double getAltitudeAtual() {
        return altitudeAtual;
    }

    public void setAltitudeAtual(double altitudeAtual) {
        this.altitudeAtual = altitudeAtual;
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

    @Override
    public String toString() {
        return "ObVelocimentroAlerta{" +
                "dateInicio=" + dateInicio +
                ", vMedia=" + vMedia +
                ", vAtual=" + vAtual +
                ", vMaxima=" + vMaxima +
                ", distancia=" + distancia +
                ", distanciaPausada=" + distanciaPausada +
                ", altitudeAtual=" + altitudeAtual +
                ", precisaoAtual=" + precisaoAtual +
                ", ganhoAltitude=" + ganhoAltitude +
                ", perdaAltitude=" + perdaAltitude +
                ", duracao=" + duracao +
                ", duracaoPausado=" + duracaoPausado +
                ", cadence=" + cadence +
                ", bpm=" + bpm +
                ", temperature=" + temperature +
                ", humidity=" + humidity +
                '}';
    }

    public String toStringNotification() {
        String time = String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(duracao),
                TimeUnit.MILLISECONDS.toMinutes(duracao) -
                        TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(duracao)),
                TimeUnit.MILLISECONDS.toSeconds(duracao) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duracao)));


        return String.format("%s - %.1fKm", time, distancia);
    }
}
