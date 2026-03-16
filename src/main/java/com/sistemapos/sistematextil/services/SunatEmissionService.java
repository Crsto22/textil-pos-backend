package com.sistemapos.sistematextil.services;

import java.util.List;

import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.util.sunat.SunatEmissionResult;

public interface SunatEmissionService {

    SunatEmissionResult emitir(Venta venta, List<VentaDetalle> detalles);
}
