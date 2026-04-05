package com.sistemapos.sistematextil.util.usuario;

public enum Rol {

    ADMINISTRADOR,
    VENTAS,
    ALMACEN,
    VENTAS_ALMACEN;

    public boolean esAdministrador() {
        return this == ADMINISTRADOR;
    }

    public boolean permiteVentas() {
        return this == VENTAS || this == VENTAS_ALMACEN || this == ADMINISTRADOR;
    }

    public boolean permiteAlmacen() {
        return this == ALMACEN || this == VENTAS_ALMACEN || this == ADMINISTRADOR;
    }

}
