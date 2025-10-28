import  boto3
import pandas as pd
from datetime import datetime, timedelta



class AwsDataCollector:
    def __init__(self,region='us-east-1'):
        self.ec2=boto3.client('ec2',region_name=region)
        self.s3=boto3.client('s3',region_name=region)
        self.ce=boto3.client('ce',region_name=region)
        self.cloudwatch=boto3.client('cloudwatch',region_name=region)
        
    def collect_data(self,days_back=60):
            data={
                'costs':self.get_details_costs(days_back),
                'resources':self.get_all_resources(),
                'utilization':self.get_resource_utilization(),
                'metadata':self.get_resource_metadata()
            }
            return data
        
    def get_details_costs(self,days_back):
            end_date=datetime.now().date()
            start_date=end_date - timedelta(days=days_back)
            costs=[]
            try:
                response=self.ce.get_cost_and_usage(
                    TimePeriod={
                        'Start':str(start_date),
                        'End':str(end_date)
                    },
                    Granularity='DAILY',
                    Metrics=['UnblendedCost','UsageQuantity','NormalizedUsageAmount'],
                    GroupBy=[
                        {'Type':'DIMENSION','Key':'SERVICE'},
                        {'Type':'DIMENSION','Key':'USAGE_TYPE'}
                    ]
                )
                for day_result in response['ResultsByTime']:
                    date=day_result['TimePeriod']['Start']
                    for group in day_result['Groups']:
                        service=group['Keys'][0]
                        usage_type=group['Keys'][1]
                        costs.append(
                            {
                                'date':date,
                                'service':service,
                                'usage_type':usage_type,
                                'cost':float(group['Metrics']['UnblendedCost']['Amount']),
                                'usage_quantity':float(group['Metrics']['UsageQuantity']['Amount']),
                                'normalized_usage':float(group['Metrics']['NormalizedUsageAmount']['Amount'])
                            }
                        )
                print(f"Collected {len(costs)} cost data from {start_date} to {end_date}")
                return pd.DataFrame(costs)
            except Exception as e:
                print(f"Error collecting cost data: {e}")
                return pd.DataFrame()
            
    def get_all_resources(self):
            resources=[]
            try:
                # --- Process EC2 Instances ---
                ec2_response=self.ec2.describe_instances()
                for reservation in ec2_response['Reservations']:
                    for instance in reservation['Instances']:
                        tags = {tag['Key']: tag['Value'] for tag in instance.get('Tags', [])}
                        resources.append({
                            # --- Standardized Fields ---
                            'resource_id': instance['InstanceId'],
                            'resource_type': 'EC2',
                            'region': instance['Placement']['AvailabilityZone'],
                            'state': instance['State']['Name'],
                            'creation_date': instance['LaunchTime'].strftime('%Y-%m-%d %H:%M'),
                            'tags': tags,
                            'name': tags.get('Name', 'N/A'),
                            # --- Specific Details ---
                            'details': {
                                'InstanceType': instance['InstanceType']
                            }
                        })
                
                # --- Process EBS Volumes ---
                ebs_response=self.ec2.describe_volumes()
                for volume in ebs_response['Volumes']:
                    tags = {tag['Key']: tag['Value'] for tag in volume.get('Tags', [])}
                    resources.append({
                            # --- Standardized Fields ---
                            'resource_id': volume['VolumeId'],
                            'resource_type': 'EBS',
                            'region': volume['AvailabilityZone'],
                            'state': volume['State'],
                            'creation_date': volume['CreateTime'].strftime('%Y-%m-%d %H:%M'),
                            'tags': tags,
                            'name': tags.get('Name', 'N/A'),
                            # --- Specific Details ---
                            'details': {
                                'Size': volume['Size'],
                                'VolumeType': volume['VolumeType'],
                                'Attached': len(volume.get('Attachments', [])) > 0
                            }
                        })

                # --- Process S3 Buckets ---
                buckets=self.s3.list_buckets()
                for bucket in buckets['Buckets']:
                    try:
                        # Get S3 bucket size (this part was correct)
                        Cw_response=self.cloudwatch.get_metric_statistics(
                            Namespace='AWS/S3',
                            MetricName='BucketSizeBytes',
                            StartTime=datetime.now() - timedelta(days=1),
                            EndTime=datetime.now(),
                            Dimensions=[
                                    {'Name': 'BucketName', 'Value': bucket['Name']},
                                    {'Name': 'StorageType', 'Value': 'StandardStorage'}
                                ],
                            Period=86400,
                            Statistics=['Average']
                        )
                        size=Cw_response['Datapoints'][0]['Average'] if Cw_response['Datapoints'] else 0
                        
                        resources.append({
                            # --- Standardized Fields ---
                            'resource_id': bucket['Name'],
                            'resource_type': 'S3',
                            'region': 'global', # S3 buckets are global, though names are unique
                            'state': 'N/A', # S3 buckets don't have a 'running/stopped' state
                            'creation_date': bucket['CreationDate'].strftime('%Y-%m-%d %H:%M'),
                            'tags': {}, # S3 bucket tags require a separate get_bucket_tagging call
                            'name': bucket['Name'],
                            # --- Specific Details ---
                            'details': {
                                'size_bytes': size
                            }
                        })
                    except Exception as e:
                        print(f"Error processing S3 bucket {bucket['Name']}: {e}")
                
                print(f"Collected {len(resources)} resources")
                return pd.DataFrame(resources)
            
            except Exception as e:
                print(f"Error collecting resources: {e}")
                return pd.DataFrame()
    def get_resource_utilization(self):
            utilization=[]
            try:
                instances=self.ec2.describe_instances(
                    Filters=[{'Name':'instance-state-name','Values':['running']}]
                ) 
                for reservation in instances['Reservations']:
                    for instance in reservation['Instances']:
                        instance_id=instance['InstanceId']
                        cpu_metrics=self.cloudwatch.get_metric_statistics(
                            Namespace='AWS/EC2',
                            MetricName='CPUUtilization',
                            Dimensions=[{'Name':'InstanceId','Value':instance_id}],
                            StartTime=datetime.now() - timedelta(days=7),
                            EndTime=datetime.now(),
                            Period=3600,
                            Statistics=['Average','Maximum','Minimum']  
                        )  
                        for data_point in cpu_metrics['Datapoints']:
                            utilization.append({
                                'ResourceId':instance_id,
                                'metric':'CPUUtilization',
                                'timestamp':data_point['Timestamp'],
                                'average':data_point['Average'],
                                'maximum':data_point['Maximum'],
                                'minimum':data_point['Minimum'],
                            })
                print(f"Collected utilization data for {len(utilization)} entries")
                return pd.DataFrame(utilization)
            except Exception as e:
                print(f"Error collecting utilization data: {e}")       
                return pd.DataFrame()
    def get_resource_metadata(self):
        print("Metadata collection not yet implemented.")
        return pd.DataFrame()
    
if __name__=="__main__":
    collector=AwsDataCollector(region='us-east-1')
    data=collector.collect_data(days_back=30)
    print("\n=== SAVING DATA TO CSV FILES ===")
    for key, df in data.items():
        if not df.empty:
            filename = f"{key}_data.csv"
            df.to_csv(filename, index=False)
            print(f"✓ Successfully saved {filename}")
        else:
            print(f"i Skipped saving empty DataFrame: {key}")
    for key,df in data.items():
        print(f"\n=== {key.upper()} DATA ===")
        print(df.head())