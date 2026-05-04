package com.sistemapos.sistematextil.util.usuario;

import com.sistemapos.sistematextil.model.SucursalTipo;

public enum Rol {

    ADMINISTRADOR,
    VENTAS,
    ALMACEN,
    VENTAS_ALMACEN,
    SISTEMA;

    public boolean esAdministrador() {
        return this == ADMINISTRADOR || this == SISTEMA;
    }

    public boolean esSistema() {
        return this == SISTEMA;
    }

    public boolean operaVentas() {
        return this == VENTAS || this == VENTAS_ALMACEN;
    }

    public boolean operaAlmacen() {
        return this == ALMACEN || this == VENTAS_ALMACEN;
    }

    public boolean permiteVentas() {
        return operaVentas() || esAdministrador();
    }

    public boolean permiteAlmacen() {
        return operaAlmacen() || esAdministrador();
    }

    public boolean esCompatibleCon(SucursalTipo tipoSucursal) {
        if (tipoSucursal == null) {
            return true;
        }
        return switch (tipoSucursal) {
            case ALMACEN -> permiteAlmacen();
            case VENTA -> permiteVentas() || this == ALMACEN;
        };
    }

    public String mensajeSucursalIncompatible(SucursalTipo tipoSucursal) {
        if (tipoSucursal == SucursalTipo.ALMACEN) {
            return "La sucursal tipo ALMACEN solo permite roles con permisos de almacen";
        }
        if (tipoSucursal == SucursalTipo.VENTA) {
            return "La sucursal tipo VENTA solo permite roles con permisos de ventas o almacen";
        }
        return "El rol no es compatible con el tipo de sucursal";
    }

}
