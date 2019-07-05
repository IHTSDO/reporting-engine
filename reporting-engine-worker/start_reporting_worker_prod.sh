export RPW_Environment=prod-int
export RPW_Version=3.6.1
nohup ./reporting_worker.sh 2>&1 | tee /var/log/supervisor/reporting_worker_startup.log