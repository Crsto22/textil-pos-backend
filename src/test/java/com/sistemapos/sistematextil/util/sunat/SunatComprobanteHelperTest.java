package com.sistemapos.sistematextil.util.sunat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.NotaCredito;
import com.sistemapos.sistematextil.model.Sucursal;
import com.sistemapos.sistematextil.model.Venta;

class SunatComprobanteHelperTest {

    @Test
    void construyeNombresYCarpetasDeFactura() {
        Venta venta = new Venta();
        venta.setTipoComprobante("FACTURA");
        venta.setSerie("F001");
        venta.setCorrelativo(1);
        venta.setSucursal(sucursalConRuc("10454562467"));

        assertEquals("10454562467-01-F001-00000001.xml", SunatComprobanteHelper.construirNombreArchivoXml(venta));
        assertEquals("10454562467-01-F001-00000001.zip", SunatComprobanteHelper.construirNombreArchivoZip(venta));
        assertEquals("R-10454562467-01-F001-00000001.zip", SunatComprobanteHelper.construirNombreArchivoCdrZip(venta));
        assertEquals("R-10454562467-01-F001-00000001.xml", SunatComprobanteHelper.construirNombreArchivoCdrXml(venta));
        assertEquals("facturas", SunatComprobanteHelper.carpetaTipoComprobante(venta));
    }

    @Test
    void construyeNombresYCarpetaDeNotaCredito() {
        NotaCredito notaCredito = new NotaCredito();
        notaCredito.setSerie("FC01");
        notaCredito.setCorrelativo(7);
        notaCredito.setSucursal(sucursalConRuc("10454562467"));

        assertEquals("10454562467-07-FC01-00000007.xml", SunatComprobanteHelper.construirNombreArchivoXml(notaCredito));
        assertEquals("10454562467-07-FC01-00000007.zip", SunatComprobanteHelper.construirNombreArchivoZip(notaCredito));
        assertEquals("R-10454562467-07-FC01-00000007.zip", SunatComprobanteHelper.construirNombreArchivoCdrZip(notaCredito));
        assertEquals("R-10454562467-07-FC01-00000007.xml", SunatComprobanteHelper.construirNombreArchivoCdrXml(notaCredito));
        assertEquals("notas-credito", SunatComprobanteHelper.carpetaTipoComprobante(notaCredito));
    }

    private Sucursal sucursalConRuc(String ruc) {
        Empresa empresa = new Empresa();
        empresa.setRuc(ruc);

        Sucursal sucursal = new Sucursal();
        sucursal.setEmpresa(empresa);
        return sucursal;
    }
}
