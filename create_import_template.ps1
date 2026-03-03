param(
    [string]$OutputBaseName = "productos_import_masivo_modelo",
    [string]$Dataset = "base"
)

$ErrorActionPreference = "Stop"

function Write-Utf8([string]$Path, [string]$Content) {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $dir = [System.IO.Path]::GetDirectoryName($fullPath)
    if ($dir -and -not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }
    [System.IO.File]::WriteAllText($fullPath, $Content, $encoding)
}

function Get-ColName([int]$n) {
    $name = ""
    while ($n -gt 0) {
        $rem = ($n - 1) % 26
        $name = [char](65 + $rem) + $name
        $n = [math]::Floor(($n - 1) / 26)
    }
    return $name
}

function Escape-Xml([string]$s) {
    if ($null -eq $s) { return "" }
    return [System.Security.SecurityElement]::Escape($s)
}

$headers = @(
    "sku",
    "nombreProducto",
    "descripcion",
    "codigoExterno",
    "categoriaNombre",
    "colorNombre",
    "colorHex",
    "tallaNombre",
    "precio",
    "stock",
    "sucursal"
)

if ($Dataset -eq "nuevos") {
    $rows = @(
        @("CHO-200-NEG-S","Chompa Oversize","Chompa tejida premium","EXT-CHO-200-NEG-S","Chompas","Negro","#111111","S",149.00,9,"Sucursal Principal"),
        @("CHO-200-NEG-M","Chompa Oversize","","EXT-CHO-200-NEG-M","Chompas","Negro","#111111","M",149.00,8,"Sucursal Principal"),
        @("CHO-200-BEI-M","Chompa Oversize","","EXT-CHO-200-BEI-M","Chompas","Beige","#D6C6A5","M",152.00,7,"Sucursal Principal"),
        @("VES-310-AZU-M","Vestido Aura","Vestido casual de temporada","EXT-VES-310-AZU-M","Vestidos","Azul Cobalto","#0047AB","M",179.90,6,"Sucursal Principal"),
        @("VES-310-AZU-L","Vestido Aura","","EXT-VES-310-AZU-L","Vestidos","Azul Cobalto","#0047AB","L",179.90,5,"Sucursal Principal"),
        @("VES-310-ROS-M","Vestido Aura","","EXT-VES-310-ROS-M","Vestidos","Rosa Palo","#E8B4BC","M",182.50,4,"Sucursal Principal"),
        @("SHU-050-BLA-36","Zapatilla Urbana","Zapatilla ligera unisex","EXT-SHU-050-BLA-36","Calzado","Blanco","#FFFFFF","36",129.00,12,"Sucursal Principal"),
        @("SHU-050-BLA-37","Zapatilla Urbana","","EXT-SHU-050-BLA-37","Calzado","Blanco","#FFFFFF","37",129.00,11,"Sucursal Principal"),
        @("SHU-050-GRI-37","Zapatilla Urbana","","EXT-SHU-050-GRI-37","Calzado","Gris Nube","","37",131.00,10,"Sucursal Principal"),
        @("POL-777-LIL-M","Polo DryFit","Polo deportivo secado rapido","EXT-POL-777-LIL-M","Polos","Lila Vibrante","#B57EDC","M",69.90,20,"Sucursal Principal"),
        @("POL-777-LIL-L","Polo DryFit","","EXT-POL-777-LIL-L","Polos","Lila Vibrante","#B57EDC","L",69.90,18,"Sucursal Principal"),
        @("POL-777-VER-M","Polo DryFit","","","Polos","Verde Lima","#32CD32","M",71.50,16,"Sucursal Principal")
    )
} else {
    $rows = @(
        @("POL-001-NEG-M","Polera Basica","Polera de algodon peinado","EXT-POL-001-NEG-M","Poleras","Negro","#111111","M",49.90,20,"Sucursal Principal"),
        @("POL-001-NEG-L","Polera Basica","","EXT-POL-001-NEG-L","Poleras","Negro","#111111","L",49.90,18,"Sucursal Principal"),
        @("POL-001-BLA-M","Polera Basica","","EXT-POL-001-BLA-M","Poleras","Blanco","#FFFFFF","M",49.90,15,"Sucursal Principal"),
        @("POL-001-BLA-L","Polera Basica","","EXT-POL-001-BLA-L","Poleras","Blanco","#FFFFFF","L",49.90,12,"Sucursal Principal"),
        @("JOG-010-GRI-M","Jogger Urban","Jogger fit regular","EXT-JOG-010-GRI-M","Pantalones","Gris","#808080","M",89.00,10,"Sucursal Principal"),
        @("JOG-010-GRI-L","Jogger Urban","","EXT-JOG-010-GRI-L","Pantalones","Gris","#808080","L",89.00,9,"Sucursal Principal"),
        @("JOG-010-AZU-M","Jogger Urban","","EXT-JOG-010-AZU-M","Pantalones","Azul Marino","#1B2A49","M",92.00,8,"Sucursal Principal"),
        @("CAM-022-CRE-M","Camisa Lino","Camisa manga larga de lino","EXT-CAM-022-CRE-M","Camisas","Crema","#F5F5DC","M",129.50,7,"Sucursal Principal"),
        @("CAM-022-CRE-L","Camisa Lino","","EXT-CAM-022-CRE-L","Camisas","Crema","#F5F5DC","L",129.50,6,"Sucursal Principal"),
        @("CAM-022-MUS-M","Camisa Lino","","EXT-CAM-022-MUS-M","Camisas","Verde Musgo","","M",132.00,5,"Sucursal Principal")
    )
}

# CSV
$csvPath = "$OutputBaseName.csv"
$objects = foreach ($r in $rows) {
    [pscustomobject]@{
        sku = $r[0]
        nombreProducto = $r[1]
        descripcion = $r[2]
        codigoExterno = $r[3]
        categoriaNombre = $r[4]
        colorNombre = $r[5]
        colorHex = $r[6]
        tallaNombre = $r[7]
        precio = $r[8]
        stock = $r[9]
        sucursal = $r[10]
    }
}
$csvLines = $objects | ConvertTo-Csv -NoTypeInformation
Write-Utf8 -Path $csvPath -Content ($csvLines -join [Environment]::NewLine)

# XLSX OpenXML
$tmp = ".tmp_xlsx_model_" + [guid]::NewGuid().ToString("N")
New-Item -ItemType Directory -Path $tmp | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tmp "_rels") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tmp "docProps") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tmp "xl") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tmp "xl\\_rels") | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tmp "xl\\worksheets") | Out-Null

$contentTypesXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>
"@
Write-Utf8 -Path (Join-Path $tmp "[Content_Types].xml") -Content $contentTypesXml

$relsXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>
"@
Write-Utf8 -Path (Join-Path $tmp "_rels/.rels") -Content $relsXml

$appXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>Microsoft Excel</Application>
</Properties>
"@
Write-Utf8 -Path (Join-Path $tmp "docProps/app.xml") -Content $appXml

$now = [DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ")
$coreXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:creator>Codex</dc:creator>
  <cp:lastModifiedBy>Codex</cp:lastModifiedBy>
  <dcterms:created xsi:type="dcterms:W3CDTF">$now</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$now</dcterms:modified>
</cp:coreProperties>
"@
Write-Utf8 -Path (Join-Path $tmp "docProps/core.xml") -Content $coreXml

$workbookXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="importacion" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>
"@
Write-Utf8 -Path (Join-Path $tmp "xl/workbook.xml") -Content $workbookXml

$workbookRelsXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>
"@
Write-Utf8 -Path (Join-Path $tmp "xl/_rels/workbook.xml.rels") -Content $workbookRelsXml

$stylesXml = @"
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf/></cellStyleXfs>
  <cellXfs count="1"><xf xfId="0"/></cellXfs>
</styleSheet>
"@
Write-Utf8 -Path (Join-Path $tmp "xl/styles.xml") -Content $stylesXml

$allRows = @()
$allRows += ,$headers
$allRows += $rows

$sheet = New-Object System.Text.StringBuilder
[void]$sheet.AppendLine('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>')
[void]$sheet.AppendLine('<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">')
[void]$sheet.AppendLine('  <sheetData>')
for ($ri = 0; $ri -lt $allRows.Count; $ri++) {
    $excelRow = $ri + 1
    [void]$sheet.AppendLine(("    <row r=""{0}"">" -f $excelRow))
    $row = $allRows[$ri]
    for ($ci = 0; $ci -lt $headers.Count; $ci++) {
        $cellRef = "{0}{1}" -f (Get-ColName ($ci + 1)), $excelRow
        $value = $row[$ci]
        $isNumber = ($ri -gt 0 -and ($ci -eq 8 -or $ci -eq 9))
        if ($isNumber) {
            $num = [string]::Format([Globalization.CultureInfo]::InvariantCulture, "{0}", $value)
            [void]$sheet.AppendLine(("      <c r=""{0}""><v>{1}</v></c>" -f $cellRef, $num))
        } else {
            $esc = Escape-Xml([string]$value)
            [void]$sheet.AppendLine(("      <c r=""{0}"" t=""inlineStr""><is><t>{1}</t></is></c>" -f $cellRef, $esc))
        }
    }
    [void]$sheet.AppendLine("    </row>")
}
[void]$sheet.AppendLine("  </sheetData>")
[void]$sheet.AppendLine("</worksheet>")
Write-Utf8 -Path (Join-Path $tmp "xl/worksheets/sheet1.xml") -Content $sheet.ToString()

$zipPath = "$OutputBaseName.zip"
$xlsxPath = "$OutputBaseName.xlsx"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
if (Test-Path $xlsxPath) { Remove-Item $xlsxPath -Force }

Push-Location $tmp
Compress-Archive -Path * -DestinationPath (Join-Path ".." $zipPath) -Force
Pop-Location

Rename-Item -Path $zipPath -NewName $xlsxPath
Remove-Item -Path $tmp -Recurse -Force

Write-Output ("OK: {0} y {1} creados" -f $xlsxPath, $csvPath)
