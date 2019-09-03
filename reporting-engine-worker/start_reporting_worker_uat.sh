#!/bin/bash
export RPW_Environment=uat-int
export RPW_Version=latest
nohup ./reporting_worker.sh 2>&1 | tee /var/log/supervisor/reporting_worker_startup.log