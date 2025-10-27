import pandas as pd
import boto3
from datetime import datetime
import sys

def deploy_test_infra():
    print("=== Infrastructure Test Script ===", flush=True)
    print("This script will create AWS resources that may incur charges.", flush=True)
    print("Estimated cost: ~$0.28/day for 2 t2.micro instances", flush=True)
    print("Your AWS credits will cover these costs.\n", flush=True)
    
    response = input("Deploy test infrastructure? (yes/no): ").lower()
    
    if response != "yes":
        print("Deployment cancelled")
        return

    # Initialize variables
    sg_id = None
    key_name = None
    volume_id = None
    bucket_name = None
    instance_ids = []
    deployment_id = datetime.now().strftime("%Y%m%d%H%M%S")

    try:
        ec2 = boto3.client('ec2', region_name='us-east-1')
        s3 = boto3.client('s3', region_name='us-east-1')
        print("✓ AWS clients created successfully\n")
    except Exception as e:
        print(f"✗ Error creating AWS clients: {str(e)}")
        return

    # Security Group
    print("Creating Security Group...")
    try:
        vpcs = ec2.describe_vpcs().get('Vpcs', [])
        vpc_id = vpcs[0]['VpcId'] if vpcs else None
        sg_resp = ec2.create_security_group(
            GroupName=f'cloud-optimizer-test-sg-{deployment_id}',
            Description='Security group for cloud optimizer test infrastructure',
            VpcId=vpc_id
        )
        sg_id = sg_resp['GroupId']
        print(f"✓ Created Security Group: {sg_id}")

        # SSH rule
        ec2.authorize_security_group_ingress(
            GroupId=sg_id,
            IpPermissions=[
                {
                    'IpProtocol': 'tcp',
                    'FromPort': 22,
                    'ToPort': 22,
                    'IpRanges': [{'CidrIp': '0.0.0.0/0'}]
                }
            ]
        )
        print("✓ Added SSH ingress rule\n")
    except Exception as e:
        print(f"✗ Error creating security group: {str(e)}\n")
        sg_id = None

    # Create key pair
    print("Creating Key Pair...")
    try:
        key_name = f'cloud_optimizer_test_key_{deployment_id}'
        key_pair = ec2.create_key_pair(KeyName=key_name)
        with open(f'{key_name}.pem', 'w') as f:
            f.write(key_pair.get('KeyMaterial', ''))
        print(f"✓ Created Key Pair: {key_name}")
        print(f"✓ Private key saved to: {key_name}.pem\n")
    except Exception as e:
        print(f"✗ Error creating key pair: {str(e)}\n")
        key_name = None

    # Find latest Amazon Linux 2 image
    print("Finding latest Amazon Linux 2 AMI...")
    try:
        images_resp = ec2.describe_images(
            Owners=['amazon'],
            Filters=[
                {'Name': 'name', 'Values': ['amzn2-ami-hvm-*-x86_64-gp2']},
                {'Name': 'state', 'Values': ['available']}
            ],
        )
        images = images_resp.get('Images', [])
        if not images:
            raise RuntimeError("No AMI images found")
        image_id = sorted(images, key=lambda x: x['CreationDate'], reverse=True)[0]['ImageId']
        print(f"✓ Found AMI: {image_id}\n")
    except Exception as e:
        print(f"✗ Error finding AMI image: {str(e)}\n")
        image_id = None

    # Launch instances
    if image_id:
        print("Launching EC2 instances...")
        for i in range(2):
            try:
                param = {
                    'ImageId': image_id,
                    'InstanceType': 't3.micro',
                    'MinCount': 1,
                    'MaxCount': 1,
                    'TagSpecifications': [
                        {
                            'ResourceType': 'instance',
                            'Tags': [
                                {'Key': 'Name', 'Value': f'test-instance-{i+1}'},
                                {'Key': 'Project', 'Value': 'cloud-optimizer'},
                                {'Key': 'Purpose', 'Value': 'cost-analysis-test'},
                                {'Key': 'Environment', 'Value': 'development'}
                            ]
                        }
                    ]
                }
                if sg_id:
                    param['SecurityGroupIds'] = [sg_id]
                if key_name:
                    param['KeyName'] = key_name
                    
                instance_resp = ec2.run_instances(**param)
                instance_id = instance_resp['Instances'][0]['InstanceId']
                instance_ids.append(instance_id)
                print(f"✓ Created EC2 Instance {i+1}: {instance_id}")
            except Exception as e:
                print(f"✗ Error launching instance {i+1}: {str(e)}")
        print()

    # Create EBS volume
    print("Creating EBS Volume...")
    try:
        volume_resp = ec2.create_volume(
            AvailabilityZone='us-east-1a',
            VolumeType='gp3',
            Size=8,
            TagSpecifications=[
                {
                    'ResourceType': 'volume',
                    'Tags': [
                        {'Key': 'Name', 'Value': 'test-unattached-volume'},
                        {'Key': 'Project', 'Value': 'cloud-optimizer'},
                        {'Key': 'Purpose', 'Value': 'orphaned-volume-test'},
                    ]
                }
            ]
        )
        volume_id = volume_resp['VolumeId']
        print(f"✓ Created EBS Volume: {volume_id}\n")
    except Exception as e:
        print(f"✗ Error creating EBS volume: {str(e)}\n")
        volume_id = None

    # Create S3 Bucket
    print("Creating S3 Bucket...")
    try:
        bucket_name = f'cloud-optimizer-test-{deployment_id}'.lower()
        s3.create_bucket(Bucket=bucket_name)
        print(f"✓ Created S3 Bucket: {bucket_name}")

        s3.put_object(
            Bucket=bucket_name,
            Key='Test_data.txt',
            Body='This is test data for cloud resources analysis'
        )
        print("✓ Uploaded test data to S3 bucket\n")
    except Exception as e:
        print(f"✗ Error creating S3 bucket: {str(e)}\n")
        bucket_name = None

    # Wait for instances
    if instance_ids:
        print('Waiting for instances to start running...')
        waiter = ec2.get_waiter('instance_running')
        try:
            waiter.wait(InstanceIds=instance_ids)
            print("✓ All instances are running\n")
        except Exception as e:
            print(f"✗ Error waiting for instances: {str(e)}\n")

    # Display summary
    print("\n" + "=" * 70)
    print("DEPLOYMENT COMPLETE!")
    print("=" * 70)
    print(f"\nDeployment ID: {deployment_id}")
    print(f"\nResources created:")
    print(f"  • EC2 Instances: {len(instance_ids)}")
    if instance_ids:
        for idx, iid in enumerate(instance_ids, 1):
            print(f"    {idx}. {iid}")
    if sg_id:
        print(f"  • Security Group: {sg_id}")
    if key_name:
        print(f"  • Key Pair: {key_name}")
    if volume_id:
        print(f"  • EBS Volume: {volume_id}")
    if bucket_name:
        print(f"  • S3 Bucket: {bucket_name}")

    print("\n" + "=" * 70)
    print("WHAT'S NEXT:")
    print("=" * 70)
    print("1. Wait 24-48 hours for Cost Explorer to collect usage data")
    print("2. These resources will generate cost and usage data")
    print("3. Check AWS Cost Explorer to see the data")
    print("4. Run your cost analysis scripts with real data!")

    print("\n" + "=" * 70)
    print("⚠️  CLEANUP INSTRUCTIONS:")
    print("=" * 70)
    print("To avoid ongoing charges, delete these resources when done:")
    print("\nOption 1 - Using AWS CLI:")
    print(f"  aws ec2 terminate-instances --instance-ids {' '.join(instance_ids)}")
    print(f"  aws ec2 delete-volume --volume-id {volume_id}")
    print(f"  aws s3 rm s3://{bucket_name} --recursive")
    print(f"  aws s3 rb s3://{bucket_name}")
    print(f"  aws ec2 delete-security-group --group-id {sg_id}")
    print(f"  aws ec2 delete-key-pair --key-name {key_name}")
    
    print("\nOption 2 - Using AWS Console:")
    print("  • EC2 → Instances → Select → Actions → Terminate")
    print("  • EC2 → Volumes → Select → Actions → Delete")
    print("  • S3 → Buckets → Select → Empty → Delete")
    print("  • EC2 → Security Groups → Select → Actions → Delete")
    print("  • EC2 → Key Pairs → Select → Actions → Delete")

    # Save deployment info
    try:
        filename = f'deployment_{deployment_id}.txt'
        with open(filename, 'w') as f:
            f.write(f"Deployment ID: {deployment_id}\n")
            f.write(f"Date: {datetime.now()}\n")
            f.write(f"Instance IDs: {','.join(instance_ids) if instance_ids else 'None'}\n")
            f.write(f"Security Group: {sg_id if sg_id else 'N/A'}\n")
            f.write(f"Key Pair: {key_name if key_name else 'N/A'}\n")
            f.write(f"Volume ID: {volume_id if volume_id else 'N/A'}\n")
            f.write(f"S3 Bucket: {bucket_name if bucket_name else 'N/A'}\n")
        print(f"\n✓ Deployment details saved to: {filename}")
    except Exception as e:
        print(f"\n✗ Failed to save deployment details: {str(e)}")

if __name__ == "__main__":
    deploy_test_infra()
    print("\n" + "=" * 70)
    print("Script execution completed.")
    print("=" * 70)