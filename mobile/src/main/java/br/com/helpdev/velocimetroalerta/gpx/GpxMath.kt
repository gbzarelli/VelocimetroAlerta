package br.com.helpdev.velocimetroalerta.gpx

import br.com.helpdev.velocimetroalerta.gpx.objects.TrkPt

object GpxMath {

    fun calculateGain(indexStartTrkPt: Int, trkPts: List<TrkPt>): Pair<Double, Double> {
        var constMediaAccuracy = 0.0
        val size = trkPts.size
        for (i in 0 until size) {
            constMediaAccuracy += trkPts[i].accuracy
        }
        constMediaAccuracy /= size

        var altA: Double
        var altB: Double
        var indexA: Int
        var indexB: Int = indexStartTrkPt
        var climb = false
        var gain = 0.0
        var loss = 0.0

        altA = trkPts[indexStartTrkPt].ele
        indexA = indexStartTrkPt

        while (indexB < trkPts.size) {
            altB = trkPts[indexB].ele
            val difAlt = altB - altA

            if (difAlt > 0 && difAlt >= constMediaAccuracy) {//GANHANDO ALTITUDE
                if (!climb) {//IF PARA ACHAR PICO NEGATIVO, QUANDO SAI DE UMA DESCIDA E INICIA UMA SUBIDA
                    loss += getGainPeak(trkPts, false, indexA, indexB, altA)
                }
                gain += difAlt
                climb = true
            } else if (difAlt < 0 && difAlt * -1 >= constMediaAccuracy) {//PERDENDO ALTITUDE
                if (climb) {//IF PARA ACHAR PICO POSITIVO, QUANDO SAI DE UMA SUBIDA E INICIA UMA DESCIDA
                    gain += getGainPeak(trkPts, true, indexA, indexB, altA)
                }
                loss += (difAlt * -1)
                climb = false
            } else {
                indexB++
                continue
            }
            //SE ENTROU EM ALGUM IF REDEFINE OS INDEX;
            indexA = indexB
            altA = altB
            indexB++
        }
        return Pair(gain, loss)
    }

    private fun getGainPeak(trkPts: List<TrkPt>, peakPositive: Boolean, indexA: Int, indexB: Int, altA: Double): Double {
        var constA = 0.0
        for (indexPeak in indexA + 1..indexB) {
            val constB = trkPts[indexPeak].ele - altA
            if (peakPositive && constA < constB || !peakPositive && constA > constB) {
                constA = constB
            }
        }
        if (peakPositive && constA > 0) {
            return constA
        } else if (!peakPositive && constA < 0) {
            return constA * -1
        }
        return 0.0
    }
}