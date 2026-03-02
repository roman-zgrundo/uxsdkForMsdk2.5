package by.agat.soldat.android.service;

interface ServiceAPI {


    //проверка связи
    long pingLong(long returnValue);

    //получить координаты последней точки клика по карте (WGS-84)
    //[B deg,L deg]
    double[] getMapClickPosWGS84();

    //получить координаты последней точки клика по карте (СК-42)
    //[B deg,L deg]
    double[] getMapClickPosSK42();

    //задать координаты последней точки клика по карте (WGS-84)
    //B deg, L deg
    void setMapClickPosWGS84(double B, double L, boolean goTo);

    //задать координаты последней точки клика по карте (СК-42)
    //B deg, L deg
    void setMapClickPosSK42(double B, double L, boolean goTo);

    //получить координаты центра карты (WGS-84)
    //[B deg,L deg]
    double[] getMapCenterWGS84();

    //получить координаты центра карты (СК-42)
    //[B deg,L deg]
    double[] getMapCenterSK42();

    //задать координаты центра карты (WGS-84)
    //B deg, L deg
    void setMapCenterWGS84(double B, double L);

    //задать координаты центра карты (СК-42)
    //B deg, L deg
    void setMapCenterSK42(double B, double L);

    //получить мои координаты (координаты моего знака на карте) (WGS-84)
    //[B deg,L deg]
    double[] getMyPosWGS84();

    //получить мои координаты (координаты моего знака на карте) (СК-42)
    //[B deg,L deg]
    double[] getMyPosSK42();

    //получить последние данные с GPS-приёмника (WGS-84)
    //[B deg,L deg, H gps m, bearing deg, speed m/s]
    double[] getLastGpsPointWGS84();

    //получить последние данные с GPS-приёмника (СК-42)
    //[B deg,L deg, H gps m, bearing deg, speed m/s]
    double[] getLastGpsPointSK42();

    //UTC time, in milliseconds since January 1, 1970
    long getLastGpsTime();

    //получить координаты выбранного объекта карты (WGS-84)
    //[B deg,L deg, H from item]
    double[] getMapItemPosWGS84();

    //получить координаты выбранного объекта карты (СК-42)
    //[B deg,L deg, H from item]
    double[] getMapItemPosSK42();

    //получить данные с матрицы высот по координатам (WGS-84)
    //H <= B deg, L deg
    double getHeightMapValueByWGS84(double B, double L);

    //получить данные с матрицы высот по координатам (СК-42)
    //H <= B deg, L deg
    double getHeightMapValueBySK42(double B, double L);

    // Команда на создание объекта: координаты + ID типа + адрес абонента + протокол передачи (1- радио, 2 - ip)
    void createObjectFromDrone(double lat, double lon, int typeId, int targetAddress, int protocolType);


}
