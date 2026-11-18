# DStudio — Launch Guide

| Launch target | How | Notes |
|---|---|---|
| **Development** | Eclipse → `.launches/DStudio_RCP_Application.launch` | PDE Runtime Workbench |
| **Debug** | Eclipse Debug Configurations (duplicate of RCP Application) | |
| **Headless server** | Eclipse `.launches/DStudio_Headless_Server.launch` or `scripts/run-headless.sh` | |
| **Tests** | Eclipse `.launches/DStudio_Tests.launch` | PDE JUnit |
| **Product (built)** | `product/eu.kalafatic.dstudio.product/target/products/eu.kalafatic.dstudio.product.id/linux/gtk/x86_64/dstudio` | needs display |

UPM detects the built product as `Product: dstudio` (kind `eclipse-product`). GUI launches were not verified headlessly; the product launcher's existence/executability was verified after build.
