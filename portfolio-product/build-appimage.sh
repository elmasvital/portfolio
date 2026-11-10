#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

PRODUCT_DIR="${REPO_ROOT}/portfolio-product/target/products/name.abuchen.portfolio.product/linux/gtk/x86_64/portfolio"
TARGET_DIR="${REPO_ROOT}/target"
APPDIR="${TARGET_DIR}/AppDir"

echo "=== Building AppImage for Portfolio Performance ==="

if [ ! -d "$PRODUCT_DIR" ]; then
    echo "ERROR: Linux product directory not found at:"
    echo "  $PRODUCT_DIR"
    echo "Please build the product first (e.g. 'mvn clean package -f portfolio-app/pom.xml ...')."
    exit 1
fi

echo "Cleaning previous AppDir..."
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/bin"
mkdir -p "$APPDIR/usr/share/applications"
mkdir -p "$APPDIR/usr/share/icons/hicolor/scalable/apps"
mkdir -p "$APPDIR/usr/share/metainfo"

echo "Copying Linux product..."
cp -r "$PRODUCT_DIR" "$APPDIR/portfolio"
chmod +x "$APPDIR/portfolio/PortfolioPerformance"

# Bundle JRE if requested and available
BUNDLE_JRE="${BUNDLE_JRE:-true}"
if [ "$BUNDLE_JRE" = "true" ]; then
    if [ -n "${JAVA_HOME:-}" ] && [ -d "$JAVA_HOME" ]; then
        echo "Bundling Java runtime from $JAVA_HOME..."
        cp -r "$JAVA_HOME" "$APPDIR/portfolio/jre"
        rm -rf "$APPDIR/portfolio/jre/demo" \
               "$APPDIR/portfolio/jre/sample" \
               "$APPDIR/portfolio/jre/man" \
               "$APPDIR/portfolio/jre/include" 2>/dev/null || true
    else
        echo "Warning: JAVA_HOME is not set or not a directory. Skipping JRE bundling."
    fi
fi

echo "Setting up desktop integration..."
cat << 'EOF' > "$APPDIR/info.portfolio_performance.PortfolioPerformance.desktop"
[Desktop Entry]
Type=Application
Name=Portfolio Performance
GenericName=Investment Portfolio Tracker
GenericName[de]=Anlageportfolio-Verwaltung
GenericName[es]=Gestor de cartera de inversiones
Comment=An open source tool to calculate the overall performance of an investment portfolio
Comment[de]=Ein Open Source Programm zur Berechnung der Performance eines Gesamtportfolios
Comment[es]=Herramienta de código abierto para calcular el rendimiento de una cartera de inversiones
Exec=PortfolioPerformance %F
Icon=info.portfolio_performance.PortfolioPerformance
Terminal=false
Categories=Office;Finance;Java;
MimeType=application/x-portfolio;
StartupWMClass=PortfolioPerformance
EOF

cp "$APPDIR/info.portfolio_performance.PortfolioPerformance.desktop" "$APPDIR/usr/share/applications/"

# Copy icon
ICON_SRC="${REPO_ROOT}/portfolio-product/icons/logo.svg"
if [ -f "$ICON_SRC" ]; then
    cp "$ICON_SRC" "$APPDIR/info.portfolio_performance.PortfolioPerformance.svg"
    cp "$ICON_SRC" "$APPDIR/usr/share/icons/hicolor/scalable/apps/info.portfolio_performance.PortfolioPerformance.svg"
    cp "$ICON_SRC" "$APPDIR/.DirIcon"
fi

# Copy AppStream metainfo
METAINFO_SRC="${REPO_ROOT}/portfolio-product/metainfo/info.portfolio_performance.PortfolioPerformance.metainfo.xml"
if [ -f "$METAINFO_SRC" ]; then
    cp "$METAINFO_SRC" "$APPDIR/usr/share/metainfo/"
fi

echo "Creating AppRun launcher..."
cat << 'EOF' > "$APPDIR/AppRun"
#!/bin/bash
set -e

HERE="$(dirname "$(readlink -f "${0}")")"

if [ -d "${HERE}/portfolio/jre" ]; then
    export JAVA_HOME="${HERE}/portfolio/jre"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    VM_ARGS=(-vm "${JAVA_HOME}/bin/java")
else
    VM_ARGS=()
fi

exec "${HERE}/portfolio/PortfolioPerformance" "${VM_ARGS[@]}" "$@"
EOF

chmod +x "$APPDIR/AppRun"

echo "Downloading appimagetool if necessary..."
mkdir -p "$TARGET_DIR"
APPIMAGETOOL="${TARGET_DIR}/appimagetool"
if ! command -v appimagetool &> /dev/null; then
    if [ ! -f "$APPIMAGETOOL" ]; then
        wget -q https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage -O "$APPIMAGETOOL"
        chmod +x "$APPIMAGETOOL"
    fi
else
    APPIMAGETOOL="appimagetool"
fi

OUTPUT_APPIMAGE="${TARGET_DIR}/PortfolioPerformance-x86_64.AppImage"
rm -f "$OUTPUT_APPIMAGE"

echo "Generating AppImage..."
ARCH=x86_64 "$APPIMAGETOOL" --appimage-extract-and-run "$APPDIR" "$OUTPUT_APPIMAGE"

echo "=== AppImage successfully created at: $OUTPUT_APPIMAGE ==="
