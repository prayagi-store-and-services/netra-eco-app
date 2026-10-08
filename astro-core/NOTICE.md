# Astro core provenance

Swiss Ephemeris Java port by Thomas Mack, 2.01.00_02. Original calculation authors: Dieter Koch and Alois Treindl, Astrodienst AG. Source archive: http://www.th-mack.de/download/swisseph-2.01.00-java-src-02.zip . Official referral: https://www.astro.com/swisseph/swedownload_e.htm .

Generated with the upstream Precompile.java using `-qfc -iswesrc -DSTRICTMATH`, Moshier ENABLED. Converted source encoding from ISO-8859-1 to UTF-8. All copyright notices preserved. No mathematical source modifications. The original archive's licence notice is preserved in NOTICE-UPSTREAM (GPL version 2 or later / professional dual licence). This application chooses the free open-source route and distributes its own code under AGPL-3.0, with the upstream notices retained. No proprietary licence claimed or paid.

This is the older Java 2.01 core, NOT current C Swiss Ephemeris 2.10.3. No JNI dependency, native ABI package, dynamic download, or remote ephemeris service. All engine calls are serialized because the Java port has shared global state. Moshier planets with Lahiri sidereal coordinates, mean Rahu and opposite Ketu, apparent geocentric positions. Ascendant uses the sidereal equal-house calculation; whole-sign houses are separately assigned from the Ascendant sign. Year length for Vimshottari periods: 365.25 days. IANA historical timezone rules come from the runtime's java.time database.

Missing/ambiguous birth time or timezone produces Unavailable. Ambiguous local times require a matching explicit offset. Nonexistent local times are rejected. Current conservative birth-year coverage is 1800-2399; unsupported input is not silently extrapolated. Polar coordinates are unsupported. Actual chart fixture validation remains a release gate.
