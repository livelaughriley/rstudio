#
# Diagnostics.R
#
# Copyright (C) 2009-19 by RStudio, Inc.
#
# Unless you have received this program directly from RStudio pursuant
# to the terms of a commercial license agreement with RStudio, then
# this program is licensed to you under the terms of version 3 of the
# GNU Affero General Public License. This program is distributed WITHOUT
# ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
# MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
# AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
#
#

# capture output into a file (note this path is in module_context::sourceDiagnostics
# so changes to the path should be synchronized there)
library(utils)
dir.create("~/rstudio-diagnostics", showWarnings=FALSE)
diagnosticsFile <- suppressWarnings(normalizePath("~/rstudio-diagnostics/diagnostics-report.txt"))

capture.output({

  # version
  versionFile <- "../VERSION"
  if (file.exists(versionFile)) {
    print(readLines(versionFile))
    cat("\n")
  }
  
  # basic info
  print(as.list(Sys.which(c("R", 
                            "pdflatex",
                            "bibtex",
                            "gcc",
                            "git", 
                            "svn"))))
  print(sessionInfo())
  cat("\nSysInfo:\n")
  print(Sys.info())
  cat("\nR Version:\n")
  print(version)
  print(as.list(Sys.getenv()))
  print(search())
  
  # locate diagnostics binary and run it
  sysName <- Sys.info()[['sysname']]
  ext <- ifelse(identical(sysName, "Windows"), ".exe", "")
  
  # first look for debug version
  cppDiag <- paste("../../../qtcreator-build/diagnostics/diagnostics",
                ext, sep="")
  if (!file.exists(cppDiag)) {
    if (identical(sysName, "Darwin"))
      cppDiag <- "../../MacOS/diagnostics"
    else
      cppDiag <- paste("../bin/diagnostics", ext, sep="")
  }
  
  if (file.exists(cppDiag)) {
    diag <- system(cppDiag, intern=TRUE)
    cat(diag, sep="\n")
  }
  
  
}, file=diagnosticsFile)

cat("Diagnostics report written to:", diagnosticsFile, "\n")


